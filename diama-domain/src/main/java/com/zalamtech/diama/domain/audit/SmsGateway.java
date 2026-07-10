package com.zalamtech.diama.domain.audit;

import java.util.Map;

/** Port d'envoi de SMS (Twilio / OVH). Adaptateur concret dans {@code diama-infra}. */
public interface SmsGateway {

    /** Envoie un SMS ; renvoie la référence fournisseur. */
    String send(String phoneE164, String templateKey, Map<String, Object> context);
}
