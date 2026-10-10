package com.zalamtech.diama.domain.kyc;

/**
 * Session de vérification Didit, exprimée en langage du domaine (aucune
 * dépendance au DTO de transport {@code diama-infra}).
 *
 * @param sessionId    identifiant Didit de la session
 * @param sessionToken jeton de session pour les SDK natifs (courte durée)
 * @param url          URL hébergée du parcours de vérification
 */
public record DiditSession(
        String sessionId,
        String sessionToken,
        String url
) {
}
