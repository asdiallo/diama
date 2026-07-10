package com.zalamtech.diama.infra.notification;

import com.zalamtech.diama.domain.audit.EmailGateway;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;

/**
 * Adaptateur e-mail (SendGrid ou SMTP). Implémentation de repli qui journalise
 * l'envoi ; à remplacer par un appel réel à l'API SendGrid / {@code quarkus-mailer}.
 */
@Slf4j
@ApplicationScoped
public class SendGridEmailGateway implements EmailGateway {

    @Override
    public String send(String email, String templateKey, Map<String, Object> context) {
        final String providerRef = UUID.randomUUID().toString();
        log.info("[EMAIL] {} → {} ref={} ctx={}", templateKey, email, providerRef, context);
        return providerRef;
    }
}
