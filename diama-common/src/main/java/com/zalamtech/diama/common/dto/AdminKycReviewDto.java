package com.zalamtech.diama.common.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Dossier KYC vu du backoffice : synthèse pour la file d'attente
 * ({@code GET /pending}, {@code documents} vide) ou dossier complet
 * ({@code GET /{userId}}, {@code documents} peuplé depuis Sumsub).
 *
 * @param kycProfileId       PK {@code kyc_profiles.id}
 * @param userId             PK {@code users.id}
 * @param email               e-mail de l'utilisateur
 * @param fullName            nom complet de l'utilisateur
 * @param kycLevel            niveau courant
 * @param verificationStatus  statut de vérification
 * @param riskCategory        {@code LOW} | {@code MEDIUM} | {@code HIGH}
 * @param sumsubApplicantId   identifiant Sumsub de l'applicant
 * @param rejectionReason     motif de rejet, si applicable
 * @param monthlyLimit        plafond mensuel courant
 * @param createdAt           date de création du profil KYC
 * @param updatedAt           dernière mise à jour
 * @param documents           documents justificatifs (dossier complet uniquement)
 */
public record AdminKycReviewDto(
        UUID kycProfileId,
        UUID userId,
        String email,
        String fullName,
        int kycLevel,
        String verificationStatus,
        String riskCategory,
        String sumsubApplicantId,
        String rejectionReason,
        BigDecimal monthlyLimit,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<KycDocumentDto> documents
) {
}
