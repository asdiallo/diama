package com.zalamtech.diama.domain.kyc;

import com.zalamtech.diama.common.dto.SumsubReviewResult;

import java.util.List;

/**
 * Port d'accès aux opérations Sumsub (KYC / vérification d'identité).
 *
 * <p>Interface définie côté domaine (architecture hexagonale) ; l'adaptateur REST
 * concret ({@code SumsubClient}) vit dans {@code diama-infra}.</p>
 */
public interface SumsubGateway {

    /** Crée un applicant Sumsub pour le niveau de vérification ciblé. */
    SumsubApplicant createApplicant(String externalUserId, String levelName);

    /** Génère un jeton d'accès SDK (mobile) pour un applicant existant. */
    String generateAccessToken(String applicantId);

    /** État de revue courant d'un applicant. */
    SumsubReviewResult getApplicantStatus(String applicantId);

    /** Documents justificatifs soumis par un applicant. */
    List<SumsubDocument> getApplicantDocuments(String applicantId);
}
