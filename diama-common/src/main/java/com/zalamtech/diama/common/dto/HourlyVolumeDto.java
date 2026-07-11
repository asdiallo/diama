package com.zalamtech.diama.common.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Volume de transferts initiés par heure, sur les 24 dernières heures. */
public record HourlyVolumeDto(
        OffsetDateTime hour,
        long txCount,
        BigDecimal volume
) {
}
