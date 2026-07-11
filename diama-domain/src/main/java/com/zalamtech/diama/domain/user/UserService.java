package com.zalamtech.diama.domain.user;

import com.zalamtech.diama.common.dto.AuditContext;
import com.zalamtech.diama.common.dto.BeneficiaryDto;
import com.zalamtech.diama.common.dto.CreateBeneficiaryDto;
import com.zalamtech.diama.common.dto.DeviceRegistrationDto;
import com.zalamtech.diama.common.dto.LimitCheckResult;
import com.zalamtech.diama.common.dto.UserProfileDto;
import com.zalamtech.diama.common.dto.UserUpdateDto;
import com.zalamtech.diama.common.exception.ResourceNotFoundException;
import com.zalamtech.diama.domain.audit.AuditService;
import com.zalamtech.diama.domain.kyc.KycProfile;
import com.zalamtech.diama.domain.kyc.KycProfileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

/**
 * Service métier du domaine utilisateur : profil, bénéficiaires, appareils et
 * plafonds KYC. Toutes les mutations sont auditées via {@link AuditService}
 * (outbox {@code audit-events}).
 *
 * <p>Les {@code userId} manipulés ici sont les PK internes {@code users.id}
 * (UUID v7), <b>pas</b> le {@code keycloak_id} : la résolution keycloak_id →
 * users.id est faite en amont par la couche API.</p>
 */
@ApplicationScoped
public class UserService {

    @Inject
    UserRepository users;

    @Inject
    UserBeneficiaryRepository beneficiaries;

    @Inject
    UserDeviceRepository devices;

    @Inject
    KycProfileRepository kycProfiles;

    @Inject
    AuditService audit;

    /** Contexte d'audit de la requête courante (résolu depuis le JWT à chaque appel). */
    @Inject
    Instance<AuditContext> auditContext;

    // ---------------------------------------------------------------------
    //  Profil
    // ---------------------------------------------------------------------

    @Transactional
    public UserProfileDto getProfile(UUID userId) {
        return toProfileDto(requireUser(userId));
    }

    @Transactional
    public UserProfileDto updateProfile(UUID userId, UserUpdateDto dto) {
        final User user = requireUser(userId);
        final UserProfileDto before = toProfileDto(user);

        // Mise à jour partielle : seuls les champs non-null sont appliqués.
        // Entité managée → le flush de fin de transaction persiste les setters.
        if (dto.firstName() != null) {
            user.setFirstName(dto.firstName());
        }
        if (dto.lastName() != null) {
            user.setLastName(dto.lastName());
        }
        if (dto.phone() != null) {
            user.setPhone(dto.phone());
        }
        if (dto.nationality() != null) {
            user.setNationality(dto.nationality());
        }
        if (dto.countryOfResidence() != null) {
            user.setCountryOfResidence(dto.countryOfResidence());
        }
        if (dto.preferredCurrency() != null) {
            user.setPreferredCurrency(dto.preferredCurrency());
        }

        final UserProfileDto after = toProfileDto(user);
        audit.log(ctx(), userId, null, "USER_PROFILE_UPDATED", "USER", userId, before, after);
        return after;
    }

    // ---------------------------------------------------------------------
    //  Bénéficiaires
    // ---------------------------------------------------------------------

    @Transactional
    public List<BeneficiaryDto> listBeneficiaries(UUID userId) {
        return beneficiaries.findActiveByUserId(userId).stream()
                .map(UserService::toBeneficiaryDto)
                .toList();
    }

    @Transactional
    public BeneficiaryDto addBeneficiary(UUID userId, CreateBeneficiaryDto dto) {
        requireUser(userId); // garantit l'existence du propriétaire

        final UserBeneficiary beneficiary = UserBeneficiary.builder()
                .userId(userId)
                .nickname(dto.nickname())
                .firstName(dto.firstName())
                .lastName(dto.lastName())
                .phoneMsisdn(dto.phoneMsisdn())
                .bankAccountNo(dto.bankAccountNo())
                .swiftBic(dto.swiftBic())
                .countryIso(dto.countryIso())
                .currency(dto.currency())
                .thunesPayerId(dto.thunesPayerId())
                .payerService(dto.payerService())
                .isActive(Boolean.TRUE)
                .build();
        beneficiaries.persist(beneficiary);

        final BeneficiaryDto result = toBeneficiaryDto(beneficiary);
        audit.log(ctx(), userId, null, "BENEFICIARY_ADDED", "BENEFICIARY", beneficiary.getId(), null, result);
        return result;
    }

    @Transactional
    public void deactivateBeneficiary(UUID userId, UUID beneficiaryId) {
        final UserBeneficiary beneficiary = beneficiaries.findByIdAndUserId(beneficiaryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Bénéficiaire", beneficiaryId));

        if (Boolean.FALSE.equals(beneficiary.getIsActive())) {
            return; // déjà désactivé : idempotent
        }

        final BeneficiaryDto before = toBeneficiaryDto(beneficiary);
        beneficiary.setIsActive(Boolean.FALSE);
        final BeneficiaryDto after = toBeneficiaryDto(beneficiary);
        audit.log(ctx(), userId, null, "BENEFICIARY_DEACTIVATED", "BENEFICIARY", beneficiaryId, before, after);
    }

    // ---------------------------------------------------------------------
    //  Appareils (push)
    // ---------------------------------------------------------------------

    @Transactional
    public void registerDevice(UUID userId, DeviceRegistrationDto dto) {
        requireUser(userId);

        // Idempotent sur (userId, deviceToken) : on réactive plutôt que dupliquer.
        final UserDevice existing = devices.findByUserIdAndToken(userId, dto.deviceToken()).orElse(null);
        if (existing != null) {
            existing.setPlatform(dto.platform());
            existing.setAppVersion(dto.appVersion());
            existing.setIsActive(Boolean.TRUE);
            audit.log(ctx(), userId, null, "DEVICE_REACTIVATED", "DEVICE", existing.getId(), null, dto);
            return;
        }

        final UserDevice device = UserDevice.builder()
                .userId(userId)
                .deviceToken(dto.deviceToken())
                .platform(dto.platform())
                .appVersion(dto.appVersion())
                .isActive(Boolean.TRUE)
                .build();
        devices.persist(device);
        audit.log(ctx(), userId, null, "DEVICE_REGISTERED", "DEVICE", device.getId(), null, dto);
    }

    // ---------------------------------------------------------------------
    //  Plafonds KYC
    // ---------------------------------------------------------------------

    /**
     * Vérifie qu'un montant tient dans le plafond mensuel de l'utilisateur.
     * Si la fenêtre mensuelle est échue, le compteur consommé est remis à zéro
     * avant le calcul. Un montant nul permet une simple consultation des plafonds.
     */
    @Transactional
    public LimitCheckResult checkMonthlyLimit(UUID userId, BigDecimal amount) {
        final KycProfile kyc = kycProfiles.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profil KYC", userId));

        resetMonthlyWindowIfElapsed(kyc);

        final BigDecimal used = kyc.getMonthlyUsed();
        final BigDecimal limit = kyc.getMonthlyLimit();
        final BigDecimal available = limit.subtract(used);
        final BigDecimal requested = amount != null ? amount : BigDecimal.ZERO;

        final boolean allowed = requested.signum() >= 0 && requested.compareTo(available) <= 0;
        final int level = kyc.getKycLevel() != null ? kyc.getKycLevel() : 0;

        return new LimitCheckResult(allowed, requested, available, used, limit, level);
    }

    /** Réinitialise consommé + date de reset si le mois de plafonnement est passé. */
    private void resetMonthlyWindowIfElapsed(KycProfile kyc) {
        final LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (!today.isBefore(kyc.getLimitResetDate())) {
            kyc.setMonthlyUsed(BigDecimal.ZERO);
            kyc.setLimitResetDate(today.with(TemporalAdjusters.firstDayOfNextMonth()));
        }
    }

    // ---------------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------------

    private User requireUser(UUID userId) {
        return users.findByIdOptional(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));
    }

    private AuditContext ctx() {
        return auditContext.isResolvable() ? auditContext.get() : null;
    }

    private static UserProfileDto toProfileDto(User u) {
        return new UserProfileDto(
                u.getId(),
                u.getEmail(),
                u.getPhone(),
                u.getFirstName(),
                u.getLastName(),
                u.getDateOfBirth(),
                u.getNationality(),
                u.getCountryOfResidence(),
                u.getPreferredCurrency(),
                u.getStatus() != null ? u.getStatus().name() : null,
                u.getSwanIban(),
                u.getReferralCode(),
                u.getCreatedAt());
    }

    private static BeneficiaryDto toBeneficiaryDto(UserBeneficiary b) {
        return new BeneficiaryDto(
                b.getId(),
                b.getNickname(),
                b.getFirstName(),
                b.getLastName(),
                b.getPhoneMsisdn(),
                b.getBankAccountNo(),
                b.getSwiftBic(),
                b.getCountryIso(),
                b.getCurrency(),
                b.getThunesPayerId(),
                b.getPayerService(),
                Boolean.TRUE.equals(b.getIsActive()),
                b.getCreatedAt());
    }
}
