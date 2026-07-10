package com.zalamtech.diama.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Compte Swan dédié (collecte EUR, IBAN individuel).
 *
 * @param accountId  identifiant Swan du compte
 * @param iban       IBAN dédié (ex. {@code FR76...})
 * @param bic        BIC de l'établissement
 * @param holderName nom du titulaire
 * @param balance    solde courant, {@code null} si non demandé
 * @param status     statut Swan du compte (ex. {@code Enabled}, {@code Suspended})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SwanAccount(
        String accountId,
        String iban,
        String bic,
        String holderName,
        BigDecimal balance,
        String status
) {
}
