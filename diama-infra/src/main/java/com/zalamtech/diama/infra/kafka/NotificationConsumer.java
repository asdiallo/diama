package com.zalamtech.diama.infra.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zalamtech.diama.domain.audit.EmailGateway;
import com.zalamtech.diama.domain.audit.NotificationRepository;
import com.zalamtech.diama.domain.audit.PushNotificationGateway;
import com.zalamtech.diama.domain.audit.SmsGateway;
import com.zalamtech.diama.domain.user.User;
import com.zalamtech.diama.domain.user.UserDevice;
import com.zalamtech.diama.domain.user.UserDeviceRepository;
import com.zalamtech.diama.domain.user.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.List;
import java.util.UUID;

/**
 * Consomme les notifications enfilées par {@code NotificationService} (topic
 * {@code notifications}) et les délivre sur le canal cible (push / SMS / e-mail).
 */
@Slf4j
@ApplicationScoped
public class NotificationConsumer {

    @Inject
    NotificationRepository notifications;

    @Inject
    UserRepository users;

    @Inject
    UserDeviceRepository devices;

    @Inject
    PushNotificationGateway pushGateway;

    @Inject
    SmsGateway smsGateway;

    @Inject
    EmailGateway emailGateway;

    @Inject
    ObjectMapper objectMapper;

    @Incoming("notifications")
    @Transactional
    public void onMessage(String json) {
        final NotificationEvent event = parse(json);
        if (event == null || event.notificationId() == null) {
            log.warn("Notification ignorée (message illisible) : {}", json);
            return;
        }

        try {
            final String providerRef = switch (event.channel()) {
                case PUSH -> sendPush(event);
                case SMS -> sendSms(event);
                case EMAIL -> sendEmail(event);
            };
            notifications.markSent(event.notificationId(), providerRef);
        } catch (RuntimeException e) {
            log.warn("Envoi de notification échoué : {} {} (id={})", event.channel(), event.templateKey(),
                    event.notificationId(), e);
            notifications.markFailed(event.notificationId());
        }
    }

    private String sendPush(NotificationEvent event) {
        final List<UserDevice> active = devices.findActiveByUserId(event.userId());
        if (active.isEmpty()) {
            throw new IllegalStateException("Aucun appareil actif pour l'utilisateur " + event.userId());
        }
        String lastRef = null;
        for (UserDevice device : active) {
            lastRef = pushGateway.send(device.getDeviceToken(), event.templateKey(), event.context());
        }
        return lastRef;
    }

    private String sendSms(NotificationEvent event) {
        final User user = findUser(event.userId());
        return smsGateway.send(user.getPhone(), event.templateKey(), event.context());
    }

    private String sendEmail(NotificationEvent event) {
        final User user = findUser(event.userId());
        return emailGateway.send(user.getEmail(), event.templateKey(), event.context());
    }

    private User findUser(UUID userId) {
        return users.findByIdOptional(userId)
                .orElseThrow(() -> new IllegalStateException("Utilisateur introuvable : " + userId));
    }

    private NotificationEvent parse(String json) {
        try {
            return objectMapper.readValue(json, NotificationEvent.class);
        } catch (Exception e) {
            log.error("Message notifications illisible", e);
            return null;
        }
    }
}
