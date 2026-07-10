package com.zalamtech.diama.domain.kyc;

/**
 * Document Sumsub tel que renvoyé par l'API applicant, exprimé en langage du
 * domaine.
 *
 * @param sumsubDocId identifiant Sumsub du document
 * @param idDocType   type de document côté Sumsub (ex. {@code PASSPORT})
 * @param country     pays d'émission (ISO 3166-1 alpha-3)
 * @param status      statut Sumsub du document (ex. {@code COMPLETED}, {@code REJECTED})
 */
public record SumsubDocument(
        String sumsubDocId,
        String idDocType,
        String country,
        String status
) {
}
