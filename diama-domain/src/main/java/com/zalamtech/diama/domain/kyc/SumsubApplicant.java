package com.zalamtech.diama.domain.kyc;

/**
 * Applicant Sumsub, exprimé en langage du domaine (aucune dépendance au DTO
 * de transport {@code diama-infra}).
 *
 * @param applicantId    identifiant Sumsub de l'applicant
 * @param externalUserId identifiant Diama envoyé à la création (users.id)
 */
public record SumsubApplicant(
        String applicantId,
        String externalUserId
) {
}
