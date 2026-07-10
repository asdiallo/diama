package com.zalamtech.diama.domain.audit;

import java.util.Map;

/** Port d'envoi d'e-mails (SendGrid / SMTP). Adaptateur concret dans {@code diama-infra}. */
public interface EmailGateway {

    /** Envoie un e-mail ; renvoie la référence fournisseur. */
    String send(String email, String templateKey, Map<String, Object> context);
}
