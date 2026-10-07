package com.zalamtech.diama.infra.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zalamtech.diama.domain.audit.AuditEvent;
import com.zalamtech.diama.domain.audit.AuditEventRepository;
import com.zalamtech.diama.domain.user.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.UUID;

/**
 * Consomme le topic {@code audit-events} (enfilé par {@code AuditService}) et
 * matérialise chaque message dans la table {@code audit_events} — sans ce
 * consumer, l'outbox publie bien sur Kafka mais rien ne persiste la trace
 * d'audit consultable par {@code AdminAuditResource}.
 */
@Slf4j
@ApplicationScoped
public class AuditEventConsumer {

    @Inject
    AuditEventRepository auditEvents;

    @Inject
    UserRepository users;

    @Inject
    ObjectMapper objectMapper;

    @Incoming("audit-events")
    @Transactional
    public void onMessage(String json) {
        final AuditEventMessage msg = parse(json);
        if (msg == null || msg.eventType() == null) {
            log.warn("Événement audit-events ignoré (illisible) : {}", json);
            return;
        }

        auditEvents.persist(AuditEvent.builder()
                .userId(existingUserOrNull(msg))
                .adminId(msg.adminId())
                .keycloakUserId(msg.keycloakUserId())
                .keycloakSessionId(msg.keycloakSessionId())
                .keycloakClient(msg.keycloakClient())
                .eventType(msg.eventType())
                .entityType(msg.entityType())
                .entityId(msg.entityId())
                .beforeState(writeOrNull(msg.before()))
                .afterState(writeOrNull(msg.after()))
                .ipAddress(msg.ip())
                .build());
    }

    /**
     * L'utilisateur peut avoir été supprimé entre l'émission et la consommation
     * (purge des inscriptions non confirmées) : on conserve la trace sans la
     * FK plutôt que de bloquer le canal sur une violation de contrainte.
     */
    private UUID existingUserOrNull(AuditEventMessage msg) {
        final UUID userId = msg.userId();
        if (userId == null || users.findByIdOptional(userId).isPresent()) {
            return userId;
        }
        log.warn("Audit {} : utilisateur {} introuvable, user_id laissé à null", msg.eventType(), userId);
        return null;
    }

    private String writeOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.toString();
    }

    private AuditEventMessage parse(String json) {
        try {
            return objectMapper.readValue(json, AuditEventMessage.class);
        } catch (Exception e) {
            log.error("Message audit-events illisible", e);
            return null;
        }
    }
}
