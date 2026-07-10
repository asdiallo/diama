package com.zalamtech.diama.domain.transaction;

/**
 * Résultat d'une opération de transfert Thunes, exprimé en langage du domaine
 * (aucune dépendance aux DTO de transport {@code diama-infra}).
 *
 * @param thunesQuotationId   devis Thunes créé pour la transaction, {@code null} si non applicable
 * @param thunesTransactionId identifiant Thunes de la transaction
 * @param status              statut renvoyé par Thunes (ex. {@code CONFIRMED}, {@code PROCESSING})
 */
public record ThunesTransferResult(
        Long thunesQuotationId,
        Long thunesTransactionId,
        String status
) {}
