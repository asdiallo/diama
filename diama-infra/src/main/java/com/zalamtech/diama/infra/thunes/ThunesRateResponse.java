package com.zalamtech.diama.infra.thunes;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Ligne de taux telle que renvoyée par l'API Thunes v2 (forme simplifiée).
 *
 * @param payerId       identifiant Thunes du payer
 * @param source        devise source (ISO 4217)
 * @param destination   devise destination (ISO 4217)
 * @param wholesaleRate taux de gros (brut)
 */
public record ThunesRateResponse(
        @JsonProperty("payer_id") Long payerId,
        @JsonProperty("source_currency") String source,
        @JsonProperty("destination_currency") String destination,
        @JsonProperty("wholesale_fx_rate") BigDecimal wholesaleRate
) {
}
