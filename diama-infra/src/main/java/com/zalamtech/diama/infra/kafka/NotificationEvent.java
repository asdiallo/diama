package com.zalamtech.diama.infra.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.zalamtech.diama.domain.audit.NotificationChannel;

import java.util.Map;
import java.util.UUID;

/** Message interne du topic {@code notifications}, enfilé par {@code NotificationService}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificationEvent(
        UUID notificationId,
        UUID userId,
        UUID transactionId,
        NotificationChannel channel,
        String templateKey,
        Map<String, Object> context
) {
}
