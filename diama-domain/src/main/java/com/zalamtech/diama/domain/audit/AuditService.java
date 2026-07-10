package com.zalamtech.diama.domain.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zalamtech.diama.common.dto.AuditContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Journalisation d'audit via le pattern <b>outbox</b>.
 *
 * <p>Chaque événement est sérialisé (états {@code before}/{@code after} en JSON
 * Jackson) puis inséré dans {@code kafka_outbox} sur le topic
 * {@code audit-events}. La publication Kafka effective est <b>asynchrone</b> :
 * elle est réalisée hors ligne par le poller d'outbox
 * ({@code KafkaOutboxRepository#findPendingForProcessing}), ce qui découple
 * l'écriture applicative de la disponibilité du broker.</p>
 *
 * <p><b>Atomicité</b> : {@link #log} rejoint la transaction de l'appelant
 * ({@code Transactional.TxType.MANDATORY}). L'entrée d'outbox est donc validée
 * <i>si et seulement si</i> la mutation métier auditée l'est aussi — garantie
 * essentielle sur une plateforme financière (pas d'audit fantôme, pas de
 * mutation non tracée).</p>
 */
@Slf4j
@ApplicationScoped
public class AuditService {

    /** Topic Kafka des événements d'audit (aligné sur {@code mp.messaging.outgoing}). */
    private static final String AUDIT_TOPIC = "audit-events";
    private static final String AGGREGATE_TYPE_DEFAULT = "AUDIT";

    @Inject
    KafkaOutboxRepository outbox;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Enregistre un événement d'audit dans l'outbox.
     *
     * @param ctx        contexte Keycloak (acteur, session, client) ; peut être {@code null}
     * @param eventType  type d'événement métier, ex. {@code USER_PROFILE_UPDATED}
     * @param entityType type d'entité concernée, ex. {@code USER}, {@code BENEFICIARY}
     * @param entityId   identifiant de l'entité (sert aussi d'{@code aggregate_id})
     * @param before     état avant mutation (sérialisé en JSON), {@code null} si création
     * @param after      état après mutation (sérialisé en JSON), {@code null} si suppression
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public void log(AuditContext ctx, String eventType, String entityType,
                    UUID entityId, Object before, Object after) {
        final String payload = buildPayload(ctx, eventType, entityType, entityId, before, after);

        final KafkaOutbox entry = KafkaOutbox.builder()
                .aggregateId(entityId)
                .aggregateType(entityType != null ? entityType : AGGREGATE_TYPE_DEFAULT)
                .eventType(eventType)
                .payload(payload)
                .kafkaTopic(AUDIT_TOPIC)
                .status(OutboxStatus.PENDING)
                .build();

        outbox.persist(entry);
        log.debug("Audit enfilé : {} {} id={}", eventType, entityType, entityId);
    }

    /** Construit le document JSON d'audit (métadonnées + états before/after). */
    private String buildPayload(AuditContext ctx, String eventType, String entityType,
                                UUID entityId, Object before, Object after) {
        final ObjectNode root = objectMapper.createObjectNode();
        root.put("eventType", eventType);
        root.put("entityType", entityType);
        root.put("entityId", entityId != null ? entityId.toString() : null);
        root.put("occurredAt", OffsetDateTime.now(ZoneOffset.UTC).toString());

        if (ctx != null) {
            root.put("keycloakUserId", ctx.keycloakUserId());
            root.put("keycloakSessionId", ctx.keycloakSessionId());
            root.put("keycloakClient", ctx.keycloakClient());
            root.put("kycLevel", ctx.kycLevel());
        }

        // valueToTree gère null → NullNode ; jamais d'exception ici.
        root.set("before", objectMapper.valueToTree(before));
        root.set("after", objectMapper.valueToTree(after));

        try {
            return objectMapper.writeValueAsString(root);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // ObjectNode déjà en mémoire : échec improbable. On ne perd pas l'audit
            // pour autant — payload minimal de repli.
            log.warn("Sérialisation d'audit impossible pour {} id={}", eventType, entityId, e);
            return "{\"eventType\":\"" + eventType + "\",\"serializationError\":true}";
        }
    }
}
