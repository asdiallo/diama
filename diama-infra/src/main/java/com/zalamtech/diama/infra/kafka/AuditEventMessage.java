package com.zalamtech.diama.infra.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/** Message du topic {@code audit-events}, publié par {@code AuditService}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuditEventMessage(
        UUID userId,
        UUID adminId,
        String keycloakUserId,
        String keycloakSessionId,
        String keycloakClient,
        String eventType,
        String entityType,
        UUID entityId,
        String ip,
        JsonNode before,
        JsonNode after
) {
}
