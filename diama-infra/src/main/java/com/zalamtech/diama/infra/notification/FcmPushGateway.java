package com.zalamtech.diama.infra.notification;

import com.zalamtech.diama.domain.audit.PushNotificationGateway;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;

/**
 * Adaptateur push FCM/APNS. Implémentation de repli qui journalise l'envoi ;
 * à remplacer par un appel réel au SDK Firebase Admin (FCM) / APNs HTTP/2
 * lors de l'intégration.
 */
@Slf4j
@ApplicationScoped
public class FcmPushGateway implements PushNotificationGateway {

    @Override
    public String send(String deviceToken, String templateKey, Map<String, Object> context) {
        final String providerRef = UUID.randomUUID().toString();
        log.info("[PUSH] {} → device={} ref={} ctx={}", templateKey, mask(deviceToken), providerRef, context);
        return providerRef;
    }

    private static String mask(String token) {
        if (token == null || token.length() <= 8) {
            return "***";
        }
        return token.substring(0, 4) + "…" + token.substring(token.length() - 4);
    }
}
