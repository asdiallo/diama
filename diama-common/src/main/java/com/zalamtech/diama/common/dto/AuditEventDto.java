package com.zalamtech.diama.common.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entrée d'audit (vue liste — les états {@code before}/{@code after} ne sont
 * pas inclus, trop volumineux pour une liste paginée).
 *
 * @param id              PK {@code audit_events.id}
 * @param userId          utilisateur final concerné, {@code null} si non applicable
 * @param adminId         agent backoffice à l'origine, {@code null} si action système/utilisateur
 * @param keycloakUserId  {@code sub} du JWT de l'acteur
 * @param keycloakClient  client Keycloak émetteur ({@code mobile-app} | {@code backoffice-app})
 * @param eventType       type d'événement métier
 * @param entityType      type d'entité concernée
 * @param entityId        identifiant de l'entité concernée
 * @param ipAddress       adresse IP d'origine
 * @param occurredAt      horodatage de l'événement
 */
public record AuditEventDto(
        UUID id,
        UUID userId,
        UUID adminId,
        String keycloakUserId,
        String keycloakClient,
        String eventType,
        String entityType,
        UUID entityId,
        String ipAddress,
        OffsetDateTime occurredAt
) {
}
