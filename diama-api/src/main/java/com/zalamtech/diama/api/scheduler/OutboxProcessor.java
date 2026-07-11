package com.zalamtech.diama.api.scheduler;

import com.zalamtech.diama.domain.audit.AuditService;
import com.zalamtech.diama.domain.audit.KafkaOutbox;
import com.zalamtech.diama.domain.audit.KafkaOutboxRepository;
import com.zalamtech.diama.domain.audit.OutboxStatus;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Traite l'outbox Kafka : publie périodiquement les entrées {@code PENDING}
 * de {@code kafka_outbox} sur leur topic respectif, puis les marque
 * {@code PUBLISHED}. Sur échec, incrémente {@code retry_count} et laisse
 * l'entrée {@code PENDING} pour reprise au cycle suivant, jusqu'à
 * {@link #MAX_RETRIES} tentatives — au-delà, passage définitif à
 * {@code FAILED} et alerte (journal d'audit, faute de canal d'alerte dédié).
 *
 * <p>Découple l'écriture applicative (transactionnelle, garantie) de la
 * disponibilité effective du broker Kafka — cœur du pattern outbox utilisé
 * par {@code AuditService}, {@code TransferEventPublisher}, {@code NotificationService}, etc.</p>
 */
@Slf4j
@ApplicationScoped
public class OutboxProcessor {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRIES = 3;
    private static final int ERROR_MESSAGE_MAX_LENGTH = 2000;

    @Inject
    KafkaOutboxRepository outbox;

    @Inject
    AuditService audit;

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

    @Inject
    @Channel("swan-webhooks")
    Emitter<String> swanWebhooks;

    @Scheduled(every = "5s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void relay() {
        final List<KafkaOutbox> pending = outbox.findPendingForProcessing(BATCH_SIZE);
        for (KafkaOutbox entry : pending) {
            publish(entry.getId());
        }
    }

    /**
     * Re-rattache l'entrée à la transaction courante avant mutation : celle
     * chargée par {@link #relay} vit dans un contexte de persistance différent
     * (méthode non transactionnelle), donc détaché — la muter directement ne
     * serait jamais flushée.
     */
    @Transactional
    void publish(UUID id) {
        final KafkaOutbox entry = outbox.findById(id);
        if (entry == null || entry.getStatus() != OutboxStatus.PENDING) {
            return; // déjà traitée entretemps (garde défensive, concurrence improbable)
        }

        try {
            emitterFor(entry.getKafkaTopic()).send(entry.getPayload());
            entry.setStatus(OutboxStatus.PUBLISHED);
            entry.setProcessedAt(OffsetDateTime.now(ZoneOffset.UTC));
        } catch (RuntimeException e) {
            handleFailure(entry, e);
        }
    }

    private void handleFailure(KafkaOutbox entry, RuntimeException e) {
        final short attempts = (short) (entry.getRetryCount() + 1);
        entry.setRetryCount(attempts);
        entry.setErrorMessage(truncate(e.getMessage()));

        log.error("Publication Kafka échouée ({}/{}) pour l'entrée outbox {} (topic {})",
                attempts, MAX_RETRIES, entry.getId(), entry.getKafkaTopic(), e);

        if (attempts >= MAX_RETRIES) {
            entry.setStatus(OutboxStatus.FAILED);
            alert(entry);
        }
        // sinon : statut laissé PENDING, repris au prochain cycle (5s).
    }

    /** Alerte backoffice — pas de canal dédié, l'entrée d'audit fait office d'alerte consultable. */
    private void alert(KafkaOutbox entry) {
        log.error("Publication Kafka abandonnée après {} tentatives : entrée outbox {} (topic {}, type {})",
                MAX_RETRIES, entry.getId(), entry.getKafkaTopic(), entry.getEventType());
        audit.log(null, null, null, "OUTBOX_PUBLISH_FAILED", "KAFKA_OUTBOX", entry.getId(), null,
                Map.of("topic", String.valueOf(entry.getKafkaTopic()),
                        "eventType", String.valueOf(entry.getEventType()),
                        "retryCount", entry.getRetryCount(),
                        "error", String.valueOf(entry.getErrorMessage())));
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > ERROR_MESSAGE_MAX_LENGTH ? message.substring(0, ERROR_MESSAGE_MAX_LENGTH) : message;
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
            case "swan-webhooks" -> swanWebhooks;
            default -> throw new IllegalStateException("Topic outbox non configuré : " + topic);
        };
    }
}
