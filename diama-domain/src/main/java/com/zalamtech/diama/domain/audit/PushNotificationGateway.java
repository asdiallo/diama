package com.zalamtech.diama.domain.audit;

import java.util.Map;

/** Port d'envoi de notifications push (FCM / APNS). Adaptateur concret dans {@code diama-infra}. */
public interface PushNotificationGateway {

    /** Envoie une notification push à un appareil ; renvoie la référence fournisseur. */
    String send(String deviceToken, String templateKey, Map<String, Object> context);
}
