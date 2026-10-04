package com.zalamtech.diama.domain.user;

import com.zalamtech.diama.common.dto.AccountRegistrationDto;
import com.zalamtech.diama.common.dto.RegistrationResultDto;
import com.zalamtech.diama.common.exception.RegistrationConflictException;
import com.zalamtech.diama.domain.audit.AuditService;
import com.zalamtech.diama.domain.support.RateLimiter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Inscription depuis l'application mobile : crée le compte dans Keycloak
 * (source de vérité de l'identité) puis la ligne {@code users} correspondante,
 * et envoie l'email de confirmation. Keycloak refuse la connexion tant que
 * l'adresse n'est pas confirmée (action requise {@code VERIFY_EMAIL}).
 *
 * <p><b>Cohérence</b> : les deux écritures ne partagent pas de transaction.
 * Keycloak est écrit en premier ; si l'insertion en base échoue, le compte
 * Keycloak est supprimé (compensation). L'inverse — une ligne {@code users}
 * sans compte Keycloak — laisserait un email réservé pour toujours, sans
 * possibilité de s'y connecter.</p>
 *
 * <p><b>Purge</b> : une inscription jamais confirmée réserve un email et un
 * téléphone. Passé {@code diama.registration.unverified-retention}, le compte
 * est supprimé des deux côtés ({@link #purgeUnverified()}). Seules les lignes
 * marquées {@code email_verification_pending} sont concernées, et
 * {@link UserSyncService} retire ce marqueur à la première requête authentifiée.</p>
 */
@Slf4j
@ApplicationScoped
public class UserRegistrationService {

    /** Taille d'un lot de purge : borne le nombre d'appels Keycloak par exécution. */
    private static final int PURGE_BATCH_SIZE = 200;

    @Inject
    UserRepository users;

    @Inject
    IdentityGateway identity;

    @Inject
    AuditService audit;

    @Inject
    RateLimiter rateLimiter;

    /** Auto-injection : nécessaire pour que l'intercepteur {@code @Transactional} s'applique. */
    @Inject
    UserRegistrationService self;

    @ConfigProperty(name = "diama.registration.unverified-retention", defaultValue = "P7D")
    Duration unverifiedRetention;

    /** Délai minimal entre deux renvois de l'email de confirmation à la même adresse. */
    @ConfigProperty(name = "diama.registration.resend-cooldown", defaultValue = "PT60S")
    Duration resendCooldown;

    /** Bilan d'une exécution de la purge. */
    public record PurgeReport(int purged, int confirmed, int failed) {
    }

    /** Projection minimale d'un compte en attente (évite de sortir une entité de sa transaction). */
    record PendingAccount(UUID id, String keycloakId) {
    }

    // ---------------------------------------------------------------------
    //  Inscription
    // ---------------------------------------------------------------------

    /** Non transactionnel : orchestre l'appel Keycloak, l'écriture en base et la compensation. */
    public RegistrationResultDto register(AccountRegistrationDto dto) {
        // Keycloak compare les emails sans casse : même normalisation ici, sinon
        // « A@x.fr » passerait la vérification d'unicité puis échouerait à la création.
        final String email = normalizeEmail(dto.email());
        final String phone = dto.phone().trim();

        final RegistrationConflictException conflict = self.findConflict(email, phone);
        if (conflict != null) {
            throw conflict;
        }

        final String keycloakId = identity.createUser(new NewIdentity(
                email, phone, dto.firstName().trim(), dto.lastName().trim(), dto.password()));

        final RegistrationResultDto result;
        try {
            result = self.persistInTx(keycloakId, email, phone, dto);
        } catch (RuntimeException e) {
            compensate(keycloakId);
            // Inscription concurrente avec le même email/téléphone : la contrainte
            // unique a tranché après notre vérification préalable.
            final RegistrationConflictException raced = self.findConflict(email, phone);
            throw raced != null ? raced : e;
        }

        // Le compte existe quoi qu'il arrive : un SMTP en panne ne doit pas faire
        // échouer l'inscription, l'utilisateur pourra demander un renvoi.
        sendVerificationQuietly(keycloakId);
        return result;
    }

    /** @return le conflit à signaler, ou {@code null} si email et téléphone sont libres. */
    @Transactional
    RegistrationConflictException findConflict(String email, String phone) {
        if (users.findByEmail(email).isPresent()) {
            return RegistrationConflictException.emailTaken();
        }
        if (users.findByPhone(phone).isPresent()) {
            return RegistrationConflictException.phoneTaken();
        }
        return null;
    }

    @Transactional
    RegistrationResultDto persistInTx(String keycloakId, String email, String phone, AccountRegistrationDto dto) {
        final User created = User.builder()
                .keycloakId(keycloakId)
                .email(email)
                .phone(phone)
                .firstName(dto.firstName().trim())
                .lastName(dto.lastName().trim())
                .countryOfResidence(dto.countryOfResidence())
                .emailVerificationPending(Boolean.TRUE)
                .build();
        users.persist(created);
        // Flush immédiat : une violation de contrainte doit remonter ici, pas au commit.
        users.flush();

        final RegistrationResultDto result = new RegistrationResultDto(created.getId(), email);
        // Pas de contexte d'audit : l'appelant n'a pas encore de JWT.
        audit.log(null, created.getId(), null, "USER_REGISTERED", "USER", created.getId(), null, result);
        return result;
    }

    // ---------------------------------------------------------------------
    //  Renvoi de l'email de confirmation
    // ---------------------------------------------------------------------

    /**
     * Renvoie l'email de confirmation si un compte en attente correspond.
     * <b>Ne signale jamais rien à l'appelant</b> (adresse inconnue, déjà confirmée,
     * renvoi trop rapproché, SMTP en panne) : l'endpoint est public et ne doit
     * pas permettre de tester l'existence d'un compte.
     */
    public void resendVerification(String rawEmail) {
        final String email = normalizeEmail(rawEmail);
        final PendingAccount account = self.findPending(email);
        if (account == null) {
            return;
        }
        if (!rateLimiter.tryAcquire("resend-verification:" + email, 1, resendCooldown)) {
            return;
        }

        try {
            final EmailVerificationState state = identity.emailVerificationState(account.keycloakId());
            if (state == EmailVerificationState.VERIFIED) {
                self.clearPending(account.id());
            } else if (state == EmailVerificationState.PENDING) {
                identity.sendVerificationEmail(account.keycloakId());
            }
        } catch (RuntimeException e) {
            log.warn("Renvoi de l'email de confirmation impossible pour {}", account.keycloakId(), e);
        }
    }

    @Transactional
    PendingAccount findPending(String email) {
        return users.findByEmail(email)
                .filter(user -> Boolean.TRUE.equals(user.getEmailVerificationPending()))
                .map(user -> new PendingAccount(user.getId(), user.getKeycloakId()))
                .orElse(null);
    }

    @Transactional
    void clearPending(UUID userId) {
        users.findByIdOptional(userId).ifPresent(user -> user.setEmailVerificationPending(Boolean.FALSE));
    }

    // ---------------------------------------------------------------------
    //  Purge des inscriptions jamais confirmées
    // ---------------------------------------------------------------------

    /**
     * Supprime (Keycloak + base) les inscriptions plus anciennes que la durée de
     * rétention dont l'email n'a jamais été confirmé. Keycloak fait foi : un
     * compte qu'il déclare confirmé est simplement démarqué, jamais supprimé.
     * Un échec sur un compte n'interrompt pas le lot ; il sera repris au passage suivant.
     */
    public PurgeReport purgeUnverified() {
        final OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minus(unverifiedRetention);
        int purged = 0;
        int confirmed = 0;
        int failed = 0;

        for (PendingAccount account : self.findPurgeCandidates(cutoff)) {
            try {
                final EmailVerificationState state = identity.emailVerificationState(account.keycloakId());
                if (state == EmailVerificationState.VERIFIED) {
                    self.clearPending(account.id());
                    confirmed++;
                } else if (state == EmailVerificationState.PENDING || state == EmailVerificationState.MISSING) {
                    self.deleteInTx(account, state == EmailVerificationState.PENDING);
                    purged++;
                } else {
                    failed++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.error("Purge impossible pour le compte {}", account.id(), e);
            }
        }
        return new PurgeReport(purged, confirmed, failed);
    }

    @Transactional
    List<PendingAccount> findPurgeCandidates(OffsetDateTime cutoff) {
        return users.findUnverifiedCreatedBefore(cutoff, PURGE_BATCH_SIZE).stream()
                .map(user -> new PendingAccount(user.getId(), user.getKeycloakId()))
                .toList();
    }

    /**
     * Base d'abord, Keycloak ensuite, dans la même transaction : si la ligne ne
     * peut pas être supprimée (données rattachées), le compte Keycloak est
     * conservé ; si Keycloak échoue, la suppression en base est annulée.
     */
    @Transactional
    void deleteInTx(PendingAccount account, boolean existsInIdentityProvider) {
        users.deleteById(account.id());
        users.flush();
        audit.log(null, account.id(), null, "USER_PURGED_UNVERIFIED", "USER", account.id(), null, null);
        if (existsInIdentityProvider) {
            identity.deleteUser(account.keycloakId());
        }
    }

    // ---------------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------------

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void sendVerificationQuietly(String keycloakId) {
        try {
            identity.sendVerificationEmail(keycloakId);
        } catch (RuntimeException e) {
            log.warn("Email de confirmation non envoyé pour {} (SMTP du realm ?)", keycloakId, e);
        }
    }

    /** Best-effort : un échec ici ne doit pas masquer l'erreur d'origine. */
    private void compensate(String keycloakId) {
        try {
            identity.deleteUser(keycloakId);
        } catch (RuntimeException e) {
            log.error("Compte Keycloak {} orphelin : suppression de compensation impossible", keycloakId, e);
        }
    }
}
