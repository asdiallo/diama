package com.zalamtech.diama.common.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Palier de frais (vue backoffice). */
public record AdminFeeDto(
        UUID id,
        String sourceCurrency,
        String destCurrency,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        BigDecimal fixedFee,
        BigDecimal percentFee,
        BigDecimal minFee,
        BigDecimal maxFee,
        boolean isActive,
        LocalDate validFrom,
        LocalDate validUntil,
        UUID createdBy,
        OffsetDateTime createdAt
) {
}
