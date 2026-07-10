package com.zalamtech.diama.domain.user;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Enregistre les sessions Keycloak actives (table user_sessions). */
@Slf4j
@ApplicationScoped
public class UserSessionService {

    @Inject
    UserSessionRepository sessions;

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
}
