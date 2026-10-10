package com.zalamtech.diama.domain.user;

import com.zalamtech.diama.common.exception.ResourceNotFoundException;
import com.zalamtech.diama.domain.audit.AuditService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/** Enregistre les sessions Keycloak actives (table user_sessions). */
@Slf4j
@ApplicationScoped
public class UserSessionService {

    @Inject
    UserSessionRepository sessions;

    @Inject
    IdentityGateway identity;

    @Inject
    AuditService audit;

    /**
     * Crée la session si elle n'existe pas encore (idempotent sur le {@code sid}).
     * La course de deux premières requêtes concurrentes est absorbée par
     * {@code ON CONFLICT DO NOTHING} : un persist() échouerait au commit, hors de tout try/catch.
     */
    @Transactional
    public void recordIfAbsent(UUID userId, String keycloakSessionId, SessionClient client,
                               String ipAddress, String userAgent) {
        // Lecture préalable : évite une tentative d'écriture à chaque requête authentifiée.
        if (keycloakSessionId == null || sessions.existsByKeycloakSessionId(keycloakSessionId)) {
            return;
        }
        if (!sessions.insertIfAbsent(userId, keycloakSessionId, client, ipAddress, userAgent)) {
            log.debug("Session déjà enregistrée pour sid={} (requête concurrente)", keycloakSessionId);
        }
    }

    /** Invalide une session (déconnexion forcée backoffice) : Keycloak + marquage local. */
    @Transactional
    public void endSession(String keycloakSessionId, UUID adminId) {
        final UserSession session = sessions.findByKeycloakSessionId(keycloakSessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session introuvable : " + keycloakSessionId));

        identity.invalidateSession(keycloakSessionId);

        session.setIsActive(Boolean.FALSE);
        session.setEndedAt(OffsetDateTime.now(ZoneOffset.UTC));

        audit.log(null, session.getUserId(), adminId, "SESSION_INVALIDATED", "SESSION", session.getId(), null,
                Map.of("keycloakSessionId", keycloakSessionId));

        log.info("Session invalidée : sid={} user={} admin={}", keycloakSessionId, session.getUserId(), adminId);
    }
}
