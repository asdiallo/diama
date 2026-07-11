package com.zalamtech.diama.infra.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zalamtech.diama.domain.user.IdentityGateway;
import com.zalamtech.diama.domain.user.SwanAccountService;
import com.zalamtech.diama.domain.user.User;
import com.zalamtech.diama.domain.user.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

/**
 * Consomme le topic {@code kyc-events} (trigger SQL {@code sync_kyc_level_to_keycloak},
 * déclenché à chaque changement de {@code kyc_level} ou {@code verification_status})
 * : seul point qui propage le niveau KYC vers Keycloak et provisionne le compte
 * Swan dédié — {@code KycService} ne le fait plus directement, pour éviter tout
 * double déclenchement.
 */
@Slf4j
@ApplicationScoped
public class KycEventConsumer {

    private static final int SWAN_PROVISIONING_LEVEL = 2;
    private static final String STATUS_APPROVED = "APPROVED";

    @Inject
    UserRepository users;

    @Inject
    IdentityGateway identity;

    @Inject
    SwanAccountService swanAccounts;

    @Inject
    ObjectMapper objectMapper;

    @Incoming("kyc-events")
    @Transactional
    public void onMessage(String json) {
        final KycEvent event = parse(json);
        if (event == null || event.userId() == null || event.kycLevel() == null) {
            log.warn("Événement kyc-events ignoré (illisible ou incomplet) : {}", json);
            return;
        }
        // Le trigger enfile aussi les rejets (verification_status seul change) : rien à
        // propager côté Keycloak/Swan dans ce cas, seule une montée APPROVED nous concerne.
        if (!STATUS_APPROVED.equalsIgnoreCase(event.verificationStatus())) {
            log.debug("Événement kyc-events ignoré (statut {})", event.verificationStatus());
            return;
        }

        final User user = users.findByIdOptional(event.userId()).orElse(null);
        if (user == null) {
            log.error("Utilisateur introuvable pour événement kyc-events : {}", event.userId());
            return;
        }

        identity.updateKycLevel(user.getKeycloakId(), event.kycLevel());

        // Compte Swan dédié requis à partir du niveau 2 (collecte EUR). Best-effort :
        // une panne Swan transitoire ne doit pas faire échouer le traitement du message.
        if (event.kycLevel() >= SWAN_PROVISIONING_LEVEL && user.getSwanAccountId() == null) {
            try {
                swanAccounts.provisionAccount(user.getId());
            } catch (RuntimeException e) {
                log.error("Provisionnement du compte Swan impossible pour user={}", user.getId(), e);
            }
        }
    }

    private KycEvent parse(String json) {
        try {
            return objectMapper.readValue(json, KycEvent.class);
        } catch (Exception e) {
            log.error("Message kyc-events illisible", e);
            return null;
        }
    }
}
