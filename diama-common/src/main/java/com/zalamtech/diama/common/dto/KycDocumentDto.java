package com.zalamtech.diama.common.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Document justificatif KYC.
 *
 * @param id               PK {@code kyc_documents.id}
 * @param docType          {@code NATIONAL_ID} | {@code PASSPORT} | {@code RESIDENCE_PERMIT} | {@code PROOF_OF_ADDRESS} | {@code SOURCE_OF_FUNDS} | {@code SELFIE}
 * @param status           {@code PENDING} | {@code VERIFIED} | {@code REJECTED}
 * @param rejectionReason  motif de rejet, si applicable
 * @param verifiedAt       date de vérification
 */
public record KycDocumentDto(
        UUID id,
        String docType,
        String status,
        String rejectionReason,
        OffsetDateTime verifiedAt
) {
}
