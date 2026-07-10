package com.zalamtech.diama.common.dto;

import java.math.BigDecimal;

/**
 * Résultat de la vérification du plafond mensuel KYC.
 *
 * @param allowed   {@code true} si le montant demandé tient dans le disponible
 * @param requested montant testé (0 pour une simple consultation des plafonds)
 * @param available reste utilisable ce mois-ci ({@code limit - used})
 * @param used      montant déjà consommé sur la période courante
 * @param limit     plafond mensuel accordé pour le niveau KYC courant
 * @param kycLevel  niveau KYC de l'utilisateur (1 à 4)
 */
public record LimitCheckResult(
        boolean allowed,
        BigDecimal requested,
        BigDecimal available,
        BigDecimal used,
        BigDecimal limit,
        int kycLevel
) {
}
