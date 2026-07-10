package com.zalamtech.diama.api.scheduler;

import com.zalamtech.diama.domain.audit.KafkaOutbox;
import com.zalamtech.diama.domain.audit.KafkaOutboxRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.util.List;

/**
 * Relais de l'outbox Kafka : publie périodiquement les entrées {@code PENDING}
 * de {@code kafka_outbox} sur leur topic Kafka respectif, puis les marque
 * {@code PUBLISHED} (ou {@code FAILED} en cas d'erreur, pour reprise ultérieure).
 *
 * <p>Découple l'écriture applicative (transactionnelle, garantie) de la
 * disponibilité effective du broker Kafka — cœur du pattern outbox utilisé
 * par {@code AuditService}, {@code TransferEventPublisher}, {@code NotificationService}, etc.</p>
 */
@Slf4j
@ApplicationScoped
public class OutboxRelay {

    private static final int BATCH_SIZE = 100;

    @Inject
    KafkaOutboxRepository outbox;

    @Inject
    @Channel("transfer-events")
    Emitter<String> transferEvents;

    @Inject
    @Channel("kyc-events")
    Emitter<String> kycEvents;

    @Inject
    @Channel("notifications")
    Emitter<String> notifications;

    @Inject
    @Channel("thunes-callbacks")
    Emitter<String> thunesCallbacks;

    @Inject
    @Channel("audit-events")
    Emitter<String> auditEvents;

    @Inject
    @Channel("rate-updated")
    Emitter<String> rateUpdated;

    @Inject
    @Channel("sumsub-webhooks")
    Emitter<String> sumsubWebhooks;

    @Scheduled(every = "2s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void relay() {
        final List<KafkaOutbox> pending = outbox.findPendingForProcessing(BATCH_SIZE);
        for (KafkaOutbox entry : pending) {
            publish(entry);
        }
    }

    @Transactional
    void publish(KafkaOutbox entry) {
        try {
            emitterFor(entry.getKafkaTopic()).send(entry.getPayload());
            outbox.markPublished(entry.getId());
        } catch (RuntimeException e) {
            log.error("Publication Kafka échouée pour l'entrée outbox {} (topic {})", entry.getId(), entry.getKafkaTopic(), e);
            outbox.markFailed(entry.getId(), e.getMessage());
        }
    }

    private Emitter<String> emitterFor(String topic) {
        return switch (topic) {
            case "transfer-events" -> transferEvents;
            case "kyc-events" -> kycEvents;
            case "notifications" -> notifications;
            case "thunes-callbacks" -> thunesCallbacks;
            case "audit-events" -> auditEvents;
            case "rate-updated" -> rateUpdated;
            case "sumsub-webhooks" -> sumsubWebhooks;
            default -> throw new IllegalStateException("Topic outbox non configuré : " + topic);
        };
    }
}
