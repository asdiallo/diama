package com.zalamtech.diama.domain.transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zalamtech.diama.domain.audit.KafkaOutbox;
import com.zalamtech.diama.domain.audit.KafkaOutboxRepository;
import com.zalamtech.diama.domain.audit.OutboxStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Publie les événements du cycle de vie d'un transfert via le pattern <b>outbox</b>
 * (topic Kafka {@code transfer-events}), à l'identique de {@code AuditService}.
 *
 * <p>{@code MANDATORY} : rejoint la transaction de l'appelant — l'événement n'est
 * enfilé que si la mutation métier est validée, garantissant l'atomicité
 * mutation ↔ event sur cette plateforme financière.</p>
 */
@Slf4j
@ApplicationScoped
public class TransferEventPublisher {

    /** Topic aligné sur {@code mp.messaging.outgoing.transfer-events}. */
    static final String TOPIC = "transfer-events";
    private static final String AGGREGATE_TYPE = "TRANSACTION";

    @Inject
    KafkaOutboxRepository outbox;

    @Inject
    ObjectMapper objectMapper;

    /** Enfile un événement transfert (ex. {@code transfer-initiated}) pour l'agrégat transaction. */
    @Transactional(Transactional.TxType.MANDATORY)
    public void publish(Transaction tx, String eventType) {
        outbox.persist(KafkaOutbox.builder()
                .aggregateId(tx.getId())
                .aggregateType(AGGREGATE_TYPE)
                .eventType(eventType)
                .payload(buildPayload(tx, eventType))
                .kafkaTopic(TOPIC)
                .status(OutboxStatus.PENDING)
                .build());
        log.debug("Événement transfert enfilé : {} tx={}", eventType, tx.getId());
    }

    private String buildPayload(Transaction tx, String eventType) {
        final ObjectNode root = objectMapper.createObjectNode();
        root.put("eventType", eventType);
        root.put("transactionId", tx.getId() != null ? tx.getId().toString() : null);
        root.put("userId", tx.getUserId() != null ? tx.getUserId().toString() : null);
        root.put("status", tx.getStatus() != null ? tx.getStatus().name() : null);
        root.put("sourceAmount", tx.getSourceAmount());
        root.put("sourceCurrency", tx.getSourceCurrency());
        root.put("destAmount", tx.getDestAmount());
        root.put("destCurrency", tx.getDestCurrency());
        root.put("thunesTransactionId", tx.getThunesTransactionId());
        root.put("occurredAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            log.warn("Sérialisation d'événement transfert impossible : {} tx={}", eventType, tx.getId(), e);
            return "{\"eventType\":\"" + eventType + "\",\"serializationError\":true}";
        }
    }
}
