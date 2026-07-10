package com.zalamtech.diama.common.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Taux de change public d'un corridor.
 *
 * @param sourceCurrency devise source (ex. {@code EUR})
 * @param destCurrency   devise destination (ex. {@code GNF})
 * @param payerId        payer Thunes associé (peut être {@code null} : taux générique)
 * @param rate           taux brut (avant marge)
 * @param clientRate     taux appliqué au client ({@code rate * (1 - spread)})
 * @param effectiveDate  date d'effet du taux
 */
public record RateDto(
        String sourceCurrency,
        String destCurrency,
        Long payerId,
        BigDecimal rate,
        BigDecimal clientRate,
        LocalDate effectiveDate
) {
}
