package com.zalamtech.diama.domain.kyc;

/**
 * Document d'identité vérifié par Didit ({@code id_verifications[]} du rapport
 * de décision), exprimé en langage du domaine.
 *
 * @param nodeId       identifiant du nœud de workflow (unique au sein d'une session)
 * @param documentType type de document Didit (ex. {@code Passport}, {@code Identity Card})
 * @param issuingState pays émetteur (ISO 3166-1 alpha-3)
 * @param status       statut Didit de la vérification ({@code Approved}, {@code Declined}, {@code In Review})
 */
public record DiditDocument(
        String nodeId,
        String documentType,
        String issuingState,
        String status
) {
}
