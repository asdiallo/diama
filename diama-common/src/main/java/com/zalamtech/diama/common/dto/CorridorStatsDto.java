package com.zalamtech.diama.common.dto;

import java.math.BigDecimal;

/** Statistiques par corridor sur 30 jours glissants (vue {@code v_corridor_stats}). */
public record CorridorStatsDto(
        String sourceCurrency,
        String destCurrency,
        long totalTransactions,
        BigDecimal totalVolumeEur,
        BigDecimal avgAmount,
        BigDecimal totalFees,
        long successful,
        long failed,
        BigDecimal successRatePct
) {
}
