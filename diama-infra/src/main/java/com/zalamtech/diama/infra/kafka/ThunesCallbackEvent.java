package com.zalamtech.diama.infra.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.zalamtech.diama.domain.transaction.TxStatus;

import java.util.UUID;

/**
 * Message interne du topic {@code thunes-callbacks} : callback Thunes déjà
 * validé et corrélé à une transaction Diama par {@code ThunesWebhookResource}
 * (module {@code diama-api}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ThunesCallbackEvent(
        UUID transactionId,
        String thunesExternalId,
        TxStatus txStatus,
        String statusMessage,
        Long thunesTransactionId
) {
}
