package com.zalamtech.diama.common.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Fonds crédités sur un compte Swan sans transaction Diama associée
 * (webhook manqué, montant erroné, virement hors contexte…).
 *
 * @param swanAccountId    compte Swan crédité
 * @param iban             IBAN dédié crédité
 * @param userId           utilisateur résolu depuis l'IBAN, {@code null} si inconnu
 * @param amount           montant orphelin
 * @param currency         devise ISO 4217
 * @param swanTransactionId identifiant Swan de la transaction
 * @param detectedAt       date de détection (passage de réconciliation)
 */
public record OrphanedFund(
        String swanAccountId,
        String iban,
        UUID userId,
        BigDecimal amount,
        String currency,
        String swanTransactionId,
        OffsetDateTime detectedAt
) {
}
