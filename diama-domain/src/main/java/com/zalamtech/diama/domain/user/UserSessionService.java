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
     * Tolère la course de deux premières requêtes concurrentes (contrainte unique).
     */
    @Transactional
    public void recordIfAbsent(UUID userId, String keycloakSessionId, SessionClient client,
                               String ipAddress, String userAgent) {
        if (keycloakSessionId == null || sessions.existsByKeycloakSessionId(keycloakSessionId)) {
            return;
        }
        try {
            final UserSession session = UserSession.builder()
                    .userId(userId)
                    .keycloakSessionId(keycloakSessionId)
                    .client(client)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .isActive(Boolean.TRUE)
                    .lastSeenAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .build();
            sessions.persist(session);
        } catch (RuntimeException e) {
            // Course concurrente probable sur keycloak_session_id (UNIQUE) : ignorable.
            log.debug("Session déjà enregistrée pour sid={} ({})", keycloakSessionId, e.getMessage());
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
