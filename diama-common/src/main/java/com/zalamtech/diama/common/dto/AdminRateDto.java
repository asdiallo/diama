package com.zalamtech.diama.common.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Taux de change (vue backoffice). */
public record AdminRateDto(
        UUID id,
        String sourceCurrency,
        String destCurrency,
        Long thunesPayerId,
        String payerName,
        BigDecimal rate,
        BigDecimal spreadPct,
        BigDecimal clientRate,
        LocalDate effectiveDate,
        boolean isActive,
        UUID updatedBy,
        OffsetDateTime fetchedAt
) {
}
