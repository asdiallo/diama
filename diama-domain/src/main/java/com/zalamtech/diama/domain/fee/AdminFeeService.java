package com.zalamtech.diama.domain.fee;

import com.zalamtech.diama.common.dto.AdminFeeDto;
import com.zalamtech.diama.common.dto.CreateFeeTierRequest;
import com.zalamtech.diama.common.exception.ResourceNotFoundException;
import com.zalamtech.diama.domain.audit.AuditService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Grille tarifaire backoffice ({@code fee_tiers}).
 *
 * <p>Les paliers sont versionnés plutôt que mutés en place : modifier un
 * palier ferme l'ancien (via {@code valid_until}) et en crée un nouveau —
 * garantit que les frais appliqués à une transaction passée restent
 * reconstituables tels qu'ils étaient au moment du transfert.</p>
 */
@Slf4j
@ApplicationScoped
public class AdminFeeService {

    @Inject
    FeeTierRepository feeTiers;

    @Inject
    AuditService audit;

    public List<AdminFeeDto> listActive() {
        return feeTiers.findAllActive().stream().map(AdminFeeService::toDto).toList();
    }

    @Transactional
    public AdminFeeDto create(CreateFeeTierRequest req, UUID adminId) {
        final FeeTier tier = FeeTier.builder()
                .sourceCurrency(req.sourceCurrency())
                .destCurrency(req.destCurrency())
                .minAmount(req.minAmount())
                .maxAmount(req.maxAmount())
                .fixedFee(req.fixedFee())
                .percentFee(req.percentFee())
                .minFee(req.minFee())
                .maxFee(req.maxFee())
                .isActive(Boolean.TRUE)
                .validFrom(req.validFrom() != null ? req.validFrom() : LocalDate.now())
                .createdBy(adminId)
                .build();
        feeTiers.persist(tier);

        audit.log(null, null, adminId, "FEE_TIER_CREATED", "FEE_TIER", tier.getId(), null, toDto(tier));
        log.info("Palier de frais créé : {} (admin={})", tier.getId(), adminId);
        return toDto(tier);
    }

    /** Ferme l'ancien palier ({@code valid_until}) et en crée un nouveau avec les valeurs fournies. */
    @Transactional
    public AdminFeeDto update(UUID tierId, CreateFeeTierRequest req, UUID adminId) {
        final FeeTier old = feeTiers.findByIdOptional(tierId)
                .orElseThrow(() -> new ResourceNotFoundException("Palier de frais", tierId));

        old.setValidUntil(LocalDate.now().minusDays(1));

        final FeeTier replacement = FeeTier.builder()
                .sourceCurrency(req.sourceCurrency())
                .destCurrency(req.destCurrency())
                .minAmount(req.minAmount())
                .maxAmount(req.maxAmount())
                .fixedFee(req.fixedFee())
                .percentFee(req.percentFee())
                .minFee(req.minFee())
                .maxFee(req.maxFee())
                .isActive(Boolean.TRUE)
                .validFrom(req.validFrom() != null ? req.validFrom() : LocalDate.now())
                .createdBy(adminId)
                .build();
        feeTiers.persist(replacement);

        audit.log(null, null, adminId, "FEE_TIER_REPLACED", "FEE_TIER", tierId, toDto(old), toDto(replacement));
        log.info("Palier de frais remplacé : {} → {} (admin={})", tierId, replacement.getId(), adminId);
        return toDto(replacement);
    }

    @Transactional
    public void deactivate(UUID tierId, UUID adminId) {
        final FeeTier tier = feeTiers.findByIdOptional(tierId)
                .orElseThrow(() -> new ResourceNotFoundException("Palier de frais", tierId));
        tier.setIsActive(Boolean.FALSE);

        audit.log(null, null, adminId, "FEE_TIER_DEACTIVATED", "FEE_TIER", tierId, null, Map.of("isActive", false));
        log.info("Palier de frais désactivé : {} (admin={})", tierId, adminId);
    }

    private static AdminFeeDto toDto(FeeTier t) {
        return new AdminFeeDto(
                t.getId(), t.getSourceCurrency(), t.getDestCurrency(), t.getMinAmount(), t.getMaxAmount(),
                t.getFixedFee(), t.getPercentFee(), t.getMinFee(), t.getMaxFee(),
                Boolean.TRUE.equals(t.getIsActive()), t.getValidFrom(), t.getValidUntil(), t.getCreatedBy(), t.getCreatedAt());
    }
}
