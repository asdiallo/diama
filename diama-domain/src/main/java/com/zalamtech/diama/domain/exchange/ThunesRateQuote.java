package com.zalamtech.diama.domain.exchange;

import java.math.BigDecimal;

/**
 * Cotation de taux brute renvoyée par Thunes pour un corridor/payer donné
 * (avant application de la marge {@code spread_pct}).
 *
 * @param payerId        identifiant Thunes du payer
 * @param sourceCurrency devise source (ex. {@code EUR})
 * @param destCurrency   devise destination (ex. {@code GNF})
 * @param rate           taux brut (unités destination par unité source)
 */
public record ThunesRateQuote(
        Long payerId,
        String sourceCurrency,
        String destCurrency,
        BigDecimal rate
) {
}
