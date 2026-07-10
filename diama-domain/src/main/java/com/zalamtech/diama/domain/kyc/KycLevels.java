package com.zalamtech.diama.domain.kyc;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Correspondance niveau KYC Diama (1 à 4) ↔ niveau Sumsub / plafond mensuel.
 *
 * <p>Les plafonds reflètent les valeurs de départ de {@code system_config}
 * ({@code kyc_levelN_monthly_limit}) ; le niveau 4 n'y est pas seedé (accès
 * illimité en pratique, plafonné ici par prudence). À terme, ces valeurs
 * devraient être lues depuis {@code system_config} plutôt que codées en dur.</p>
 */
final class KycLevels {

    private static final Map<Integer, String> SUMSUB_LEVEL_NAMES = Map.of(
            1, "basic-kyc-level",
            2, "standard-kyc-level",
            3, "enhanced-kyc-level",
            4, "full-kyc-level");

    private static final Map<Integer, BigDecimal> MONTHLY_LIMITS = Map.of(
            1, new BigDecimal("300"),
            2, new BigDecimal("3000"),
            3, new BigDecimal("15000"),
            4, new BigDecimal("50000"));

    static final int MAX_LEVEL = 4;

    private KycLevels() {
    }

    static String sumsubLevelName(int level) {
        return SUMSUB_LEVEL_NAMES.getOrDefault(clamp(level), SUMSUB_LEVEL_NAMES.get(1));
    }

    static BigDecimal monthlyLimit(int level) {
        return MONTHLY_LIMITS.getOrDefault(clamp(level), MONTHLY_LIMITS.get(1));
    }

    private static int clamp(int level) {
        return Math.max(1, Math.min(level, MAX_LEVEL));
    }
}
