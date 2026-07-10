package com.zalamtech.diama.infra.thunes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Payer Thunes tel que renvoyé par {@code GET /v2/money-transfer/payers}
 * (forme simplifiée). Traduit en entité {@code ThunesPayer} par {@code ThunesClient}.
 *
 * @param id             identifiant Thunes natif du payer
 * @param name           nom commercial de l'opérateur
 * @param countryIsoCode pays desservi (ISO 3166-1 alpha-2)
 * @param currency       devise de payout (ISO 4217)
 * @param service        type de service ({@code MobileWallet} | {@code BankAccount} | {@code CashPickup})
 * @param minAmount      montant minimum accepté (devise source)
 * @param maxAmount      montant maximum accepté (devise source)
 * @param precision      nombre de décimales du montant de payout
 * @param increment      incrément de montant autorisé
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ThunesPayerResponse(
        @JsonProperty("id") Long id,
        @JsonProperty("name") String name,
        @JsonProperty("country_iso_code") String countryIsoCode,
        @JsonProperty("currency") String currency,
        @JsonProperty("service") String service,
        @JsonProperty("min_amount") BigDecimal minAmount,
        @JsonProperty("max_amount") BigDecimal maxAmount,
        @JsonProperty("precision") Short precision,
        @JsonProperty("increment") BigDecimal increment
) {
}
