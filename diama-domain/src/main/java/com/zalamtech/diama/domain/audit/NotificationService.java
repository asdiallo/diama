package com.zalamtech.diama.domain.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zalamtech.diama.domain.user.UserDeviceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Notifie un utilisateur d'un événement métier (transfert, RFI, KYC) via le
 * pattern <b>outbox</b> (topic Kafka {@code notifications}), à l'identique de
 * {@link AuditService}.
 *
 * <p>{@code MANDATORY} : rejoint la transaction de l'appelant — la notification
 * n'est enfilée que si l'événement métier qu'elle annonce est lui-même validé
 * (pas d'e-mail "transfert livré" pour un transfert dont l'écriture a échoué).</p>
 */
@Slf4j
@ApplicationScoped
public class NotificationService {

    private static final String TOPIC = "notifications";

    /** Templates jugés assez critiques pour justifier un SMS en plus du push/e-mail. */
    private static final Set<String> SMS_TEMPLATES = Set.of(
            "TRANSFER_DELIVERED", "TRANSFER_FAILED", "TRANSFER_REFUNDED", "RFI_DOCUMENT_REQUIRED");

    @Inject
    NotificationRepository notifications;

    @Inject
    KafkaOutboxRepository outbox;

    @Inject
    UserDeviceRepository devices;

    @Inject
    ObjectMapper objectMapper;

    /** Notifie l'utilisateur sur les canaux pertinents pour ce template. */
    @Transactional(Transactional.TxType.MANDATORY)
    public void send(UUID userId, UUID txId, String templateKey, Map<String, Object> ctx) {
        for (NotificationChannel channel : selectChannels(userId, templateKey)) {
            enqueue(userId, txId, channel, templateKey, ctx);
        }
    }

    private Set<NotificationChannel> selectChannels(UUID userId, String templateKey) {
        final EnumSet<NotificationChannel> channels = EnumSet.of(NotificationChannel.EMAIL);
        if (!devices.findActiveByUserId(userId).isEmpty()) {
            channels.add(NotificationChannel.PUSH);
        }
        if (SMS_TEMPLATES.contains(templateKey)) {
            channels.add(NotificationChannel.SMS);
        }
        return channels;
    }

    private void enqueue(UUID userId, UUID txId, NotificationChannel channel, String templateKey, Map<String, Object> ctx) {
        final Notification notification = Notification.builder()
                .userId(userId)
                .transactionId(txId)
                .channel(channel)
                .templateKey(templateKey)
                .context(writeContext(ctx))
                .status(NotificationStatus.PENDING)
                .build();
        notifications.persist(notification);

        outbox.persist(KafkaOutbox.builder()
                .aggregateId(notification.getId())
                .aggregateType("NOTIFICATION")
                .eventType(templateKey)
                .payload(buildPayload(notification, ctx))
                .kafkaTopic(TOPIC)
                .status(OutboxStatus.PENDING)
                .build());

        log.debug("Notification enfilée : {} {} user={} tx={}", channel, templateKey, userId, txId);
    }

    private String buildPayload(Notification notification, Map<String, Object> ctx) {
        final ObjectNode root = objectMapper.createObjectNode();
        root.put("notificationId", notification.getId().toString());
        root.put("userId", notification.getUserId().toString());
        root.put("transactionId", notification.getTransactionId() != null ? notification.getTransactionId().toString() : null);
        root.put("channel", notification.getChannel().name());
        root.put("templateKey", notification.getTemplateKey());
        root.set("context", objectMapper.valueToTree(ctx != null ? ctx : Map.of()));
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            log.warn("Sérialisation de notification impossible : {} id={}", notification.getTemplateKey(), notification.getId(), e);
            return "{\"templateKey\":\"" + notification.getTemplateKey() + "\",\"serializationError\":true}";
        }
    }

    private String writeContext(Map<String, Object> ctx) {
        try {
            return objectMapper.writeValueAsString(ctx != null ? ctx : Map.of());
        } catch (JsonProcessingException e) {
            log.warn("Sérialisation du contexte de notification impossible", e);
            return "{}";
        }
    }
}
