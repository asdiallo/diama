package com.zalamtech.diama.domain.fee;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Résultat du calcul de frais pour un montant et un corridor.
 *
 * @param fixedFee    composante fixe du palier appliqué
 * @param percentFee  composante proportionnelle calculée ({@code percent_fee * montant})
 * @param totalFee    frais réellement facturés (après {@code MAX}, plancher et plafond)
 * @param tierApplied identifiant du palier {@code fee_tiers} appliqué
 */
public record FeeResult(
        BigDecimal fixedFee,
        BigDecimal percentFee,
        BigDecimal totalFee,
        UUID tierApplied
) {
}
