package com.zalamtech.diama.infra.thunes;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Corps de {@code POST /v2/money-transfer/quotations} (API Thunes v2, forme
 * simplifiée pour le corridor Europe → Afrique de l'Ouest).
 *
 * @param externalId      identifiant idempotent côté Diama
 * @param payerId         payer Thunes cible (détermine pays/devise de payout)
 * @param mode            {@code SOURCE_AMOUNT} (on fige le montant envoyé)
 * @param transactionType type de transaction Thunes (ex. {@code C2C})
 * @param source          montant/devise/pays source
 * @param destination     devise/pays de destination
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ThunesQuotationRequest(
        @JsonProperty("external_id") String externalId,
        @JsonProperty("payer_id") Long payerId,
        @JsonProperty("mode") String mode,
        @JsonProperty("transaction_type") String transactionType,
        @JsonProperty("source") Source source,
        @JsonProperty("destination") Destination destination
) {

    public record Source(
            @JsonProperty("currency") String currency,
            @JsonProperty("country_iso_code") String countryIsoCode,
            @JsonProperty("amount") BigDecimal amount
    ) {
    }

    public record Destination(
            @JsonProperty("currency") String currency,
            @JsonProperty("country_iso_code") String countryIsoCode
    ) {
    }
}
