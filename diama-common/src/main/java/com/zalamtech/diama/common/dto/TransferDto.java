package com.zalamtech.diama.common.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Détail d'un transfert renvoyé au client mobile.
 *
 * @param id                 identifiant interne du transfert (transactions.id)
 * @param status             état courant (statut + motif d'échec + horodatages)
 * @param sourceAmount       montant envoyé (devise source)
 * @param sourceCurrency     devise source (ex. {@code EUR})
 * @param destAmount         montant reçu (devise destination)
 * @param destCurrency       devise destination (ex. {@code GNF})
 * @param exchangeRate       taux client appliqué
 * @param feeAmount          frais de transfert (devise source)
 * @param feeCurrency        devise des frais (ex. {@code EUR})
 * @param totalDebit         total débité = {@code sourceAmount + feeAmount}
 * @param payerName          opérateur de payout Thunes, {@code null} si inconnu
 * @param beneficiaryId      bénéficiaire destinataire
 * @param thunesTransactionId référence Thunes une fois confirmé, sinon {@code null}
 * @param createdAt          date de création du transfert
 */
public record TransferDto(
        UUID id,
        TransferStatusDto status,
        BigDecimal sourceAmount,
        String sourceCurrency,
        BigDecimal destAmount,
        String destCurrency,
        BigDecimal exchangeRate,
        BigDecimal feeAmount,
        String feeCurrency,
        BigDecimal totalDebit,
        String payerName,
        UUID beneficiaryId,
        Long thunesTransactionId,
        OffsetDateTime createdAt
) {
}
