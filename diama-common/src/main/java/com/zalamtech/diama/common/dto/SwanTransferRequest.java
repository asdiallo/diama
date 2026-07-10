package com.zalamtech.diama.common.dto;

import java.math.BigDecimal;

/**
 * Requête de virement sortant Swan (ex. remboursement vers l'IBAN expéditeur).
 *
 * @param sourceAccountId   compte Swan émetteur
 * @param destinationIban   IBAN bénéficiaire
 * @param destinationName   nom du bénéficiaire
 * @param amount            montant du virement
 * @param currency          devise ISO 4217
 * @param reference         libellé du virement
 */
public record SwanTransferRequest(
        String sourceAccountId,
        String destinationIban,
        String destinationName,
        BigDecimal amount,
        String currency,
        String reference
) {
}
