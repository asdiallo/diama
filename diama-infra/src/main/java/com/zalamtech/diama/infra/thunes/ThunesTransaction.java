package com.zalamtech.diama.infra.thunes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Réponse transaction Thunes ({@code POST .../transactions} et
 * {@code GET /v2/money-transfer/transactions/{id}}).
 *
 * @param id                       identifiant Thunes de la transaction
 * @param externalId               identifiant idempotent Diama renvoyé en écho
 * @param status                   statut Thunes (ex. {@code CONFIRMED}, {@code PROCESSING}, {@code COMPLETED})
 * @param statusMessage            libellé de statut détaillé
 * @param payerId                  payer Thunes utilisé
 * @param payerTransactionReference référence de transaction chez le payer local
 * @param creationDate             date de création (ISO-8601)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ThunesTransaction(
        @JsonProperty("id") Long id,
        @JsonProperty("external_id") String externalId,
        @JsonProperty("status") String status,
        @JsonProperty("status_message") String statusMessage,
        @JsonProperty("payer_id") Long payerId,
        @JsonProperty("payer_transaction_reference") String payerTransactionReference,
        @JsonProperty("creation_date") String creationDate
) {
}
