package com.zalamtech.diama.common.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** @param spreadPct nouvelle marge, en fraction (ex. {@code 0.02} pour 2 %) */
public record UpdateSpreadRequest(
        @NotNull
        @DecimalMin(value = "0.0", inclusive = true)
        @DecimalMax(value = "1.0", inclusive = true)
        BigDecimal spreadPct
) {
}
