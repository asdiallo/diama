package com.zalamtech.diama.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Virement Swan (sortant — {@code initiateTransfer} — ou entrant, tel que
 * renvoyé par {@code getTransaction} / le relevé de compte utilisé par la
 * réconciliation).
 *
 * @param transferId identifiant Swan du virement
 * @param accountId  compte Swan concerné (émetteur si sortant, destinataire si entrant)
 * @param status     statut Swan (ex. {@code Booked}, {@code Rejected}, {@code Pending})
 * @param amount     montant
 * @param currency   devise ISO 4217
 * @param createdAt  horodatage Swan
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SwanTransfer(
        String transferId,
        String accountId,
        String status,
        BigDecimal amount,
        String currency,
        OffsetDateTime createdAt
) {
}
