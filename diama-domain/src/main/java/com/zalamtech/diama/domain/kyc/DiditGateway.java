package com.zalamtech.diama.domain.kyc;

import java.util.List;

/**
 * Port d'accès aux opérations Didit (KYC / vérification d'identité).
 *
 * <p>Interface définie côté domaine (architecture hexagonale) ; l'adaptateur REST
 * concret ({@code DiditClient}) vit dans {@code diama-infra}.</p>
 */
public interface DiditGateway {

    /**
     * Crée une session de vérification pour le workflow donné.
     *
     * @param workflowId workflow Didit (UUID) correspondant au niveau KYC visé
     * @param vendorData identifiant Diama de l'utilisateur (users.id), renvoyé tel quel
     *                   dans les webhooks et utilisé par Didit pour la détection de doublons
     */
    DiditSession createSession(String workflowId, String vendorData);

    /** Documents d'identité vérifiés dans une session (rapport de décision). */
    List<DiditDocument> getDocuments(String sessionId);
}
