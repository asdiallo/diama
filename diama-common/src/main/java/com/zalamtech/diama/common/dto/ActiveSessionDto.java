package com.zalamtech.diama.common.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Session Keycloak active (vue {@code v_active_sessions}, monitoring backoffice temps réel).
 *
 * @param id                 PK {@code user_sessions.id}
 * @param keycloakSessionId  {@code sid} du JWT
 * @param client             {@code mobile-app} | {@code backoffice-app}
 * @param ipAddress          adresse IP de connexion
 * @param startedAt          début de session
 * @param lastSeenAt         dernière activité observée
 * @param email              e-mail de l'utilisateur
 * @param keycloakId         {@code sub} Keycloak de l'utilisateur
 * @param kycLevel           niveau KYC courant
 */
public record ActiveSessionDto(
        UUID id,
        String keycloakSessionId,
        String client,
        String ipAddress,
        OffsetDateTime startedAt,
        OffsetDateTime lastSeenAt,
        String email,
        String keycloakId,
        Short kycLevel
) {
}
