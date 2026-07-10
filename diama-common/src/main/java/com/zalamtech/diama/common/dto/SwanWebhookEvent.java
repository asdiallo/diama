package com.zalamtech.diama.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Corps du webhook Swan (réception de fonds sur un compte dédié).
 *
 * <p>Simplifié par rapport au format réel Swan (événements imbriqués,
 * signature JWS asymétrique) pour rester cohérent avec le reste de
 * l'intégration webhooks Diama (HMAC partagé, payload plat).</p>
 *
 * @param eventType        {@code TRANSACTION_BOOKED} | {@code TRANSACTION_REJECTED}
 * @param accountId        compte Swan crédité
 * @param iban             IBAN dédié crédité (corrélation utilisateur)
 * @param transactionId    identifiant Swan de la transaction
 * @param amount           montant reçu
 * @param currency         devise ISO 4217
 * @param counterpartyName nom de l'émetteur du virement entrant
 * @param reference        libellé du virement
 * @param rejectedReason   motif de rejet, si {@code eventType == TRANSACTION_REJECTED}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SwanWebhookEvent(
        String eventType,
        String accountId,
        String iban,
        String transactionId,
        BigDecimal amount,
        String currency,
        String counterpartyName,
        String reference,
        String rejectedReason
) {
}
