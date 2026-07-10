package com.zalamtech.diama.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Corps du webhook Sumsub (type {@code applicantReviewed} principalement ;
 * les autres types — {@code applicantCreated}, {@code applicantPending}, … —
 * sont reçus mais ignorés par {@code KycService.handleSumsubWebhook}).
 *
 * @param applicantId    identifiant Sumsub de l'applicant
 * @param externalUserId identifiant Diama envoyé à la création (users.id)
 * @param type           type d'événement, ex. {@code applicantReviewed}
 * @param reviewStatus   {@code init} | {@code pending} | {@code completed} | {@code onHold}
 * @param reviewResult   détail de la revue (présent si {@code type == applicantReviewed})
 * @param sandboxMode    {@code true} en environnement sandbox Sumsub
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SumsubWebhookPayload(
        String applicantId,
        String externalUserId,
        String type,
        String reviewStatus,
        ReviewResult reviewResult,
        Boolean sandboxMode
) {

    /**
     * @param reviewAnswer      {@code GREEN} | {@code RED}
     * @param rejectLabels      motifs de rejet Sumsub
     * @param reviewRejectType  {@code RETRY} (corrigible) | {@code FINAL}
     * @param moderationComment commentaire libre du modérateur
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReviewResult(
            String reviewAnswer,
            List<String> rejectLabels,
            String reviewRejectType,
            String moderationComment
    ) {
    }
}
