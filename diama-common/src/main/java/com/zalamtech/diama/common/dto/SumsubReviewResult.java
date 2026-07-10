package com.zalamtech.diama.common.dto;

import java.util.List;

/**
 * Résultat de revue Sumsub, tel que renvoyé par {@code GET /resources/applicants/{id}/status}
 * ou porté par le webhook {@code applicantReviewed}.
 *
 * @param applicantId       identifiant Sumsub de l'applicant
 * @param reviewStatus      {@code init} | {@code pending} | {@code completed} | {@code onHold}
 * @param reviewAnswer      {@code GREEN} (approuvé) | {@code RED} (rejeté), {@code null} si revue non terminée
 * @param rejectLabels      motifs de rejet Sumsub, si {@code reviewAnswer == RED}
 * @param moderationComment commentaire libre du modérateur Sumsub
 */
public record SumsubReviewResult(
        String applicantId,
        String reviewStatus,
        String reviewAnswer,
        List<String> rejectLabels,
        String moderationComment
) {
}
