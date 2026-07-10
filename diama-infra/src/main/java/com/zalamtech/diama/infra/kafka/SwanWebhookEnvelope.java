package com.zalamtech.diama.infra.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Message interne du topic {@code swan-webhooks} : webhook Swan déjà validé
 * et corrélé à une transaction Diama par {@code SwanWebhookResource}
 * (module {@code diama-api}), {@code transactionId} pouvant être {@code null}
 * si aucune transaction {@code INITIATED} n'a pu être appariée (fonds orphelins
 * — laissés à {@code FundsVerificationService}, pas retentés ici).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SwanWebhookEnvelope(
        UUID transactionId,
        String eventType,
        String swanAccountId,
        String swanTransactionId,
        BigDecimal amount,
        String currency,
        String rejectedReason
) {
}
