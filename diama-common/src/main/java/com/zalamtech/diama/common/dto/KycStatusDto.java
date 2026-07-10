package com.zalamtech.diama.common.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Statut KYC de l'utilisateur connecté.
 *
 * @param kycLevel           niveau courant (0 si le parcours n'a jamais été démarré)
 * @param verificationStatus {@code NOT_STARTED} | {@code PENDING} | {@code APPROVED} | {@code REJECTED} | {@code ON_HOLD}
 * @param monthlyLimit       plafond mensuel courant (EUR)
 * @param monthlyUsed        montant déjà consommé ce mois-ci
 * @param remainingLimit     {@code monthlyLimit - monthlyUsed}
 * @param rejectionReason    motif de rejet, si applicable
 * @param levelGrantedAt     date d'obtention du niveau courant
 */
public record KycStatusDto(
        int kycLevel,
        String verificationStatus,
        BigDecimal monthlyLimit,
        BigDecimal monthlyUsed,
        BigDecimal remainingLimit,
        String rejectionReason,
        OffsetDateTime levelGrantedAt
) {
}
