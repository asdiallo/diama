package com.zalamtech.diama.infra.thunes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Réponse de {@code POST /v2/money-transfer/quotations} : devis Thunes verrouillé
 * (identifié par {@link #id()}, à réutiliser pour créer la transaction).
 *
 * @param id              identifiant du devis Thunes
 * @param externalId      identifiant idempotent renvoyé en écho
 * @param payerId         payer Thunes du devis
 * @param wholesaleFxRate taux de gros appliqué
 * @param expirationDate  expiration du devis (ISO-8601)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ThunesQuotation(
        @JsonProperty("id") Long id,
        @JsonProperty("external_id") String externalId,
        @JsonProperty("payer_id") Long payerId,
        @JsonProperty("wholesale_fx_rate") BigDecimal wholesaleFxRate,
        @JsonProperty("expiration_date") String expirationDate
) {
}
