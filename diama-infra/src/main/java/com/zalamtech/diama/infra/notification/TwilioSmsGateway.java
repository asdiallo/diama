package com.zalamtech.diama.infra.notification;

import com.zalamtech.diama.domain.audit.SmsGateway;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;

/**
 * Adaptateur SMS (Twilio ou OVH SMS). Implémentation de repli qui journalise
 * l'envoi ; à remplacer par un appel réel au SDK/API du fournisseur retenu.
 */
@Slf4j
@ApplicationScoped
public class TwilioSmsGateway implements SmsGateway {

    @Override
    public String send(String phoneE164, String templateKey, Map<String, Object> context) {
        final String providerRef = UUID.randomUUID().toString();
        log.info("[SMS] {} → {} ref={} ctx={}", templateKey, phoneE164, providerRef, context);
        return providerRef;
    }
}
