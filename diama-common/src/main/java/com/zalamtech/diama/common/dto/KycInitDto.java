package com.zalamtech.diama.common.dto;

/**
 * Réponse à l'initialisation du parcours KYC : session de vérification Didit
 * à ouvrir côté mobile (SDK natif via {@code sessionToken}, ou WebView /
 * navigateur via {@code verificationUrl}).
 *
 * @param sessionId       identifiant de la session Didit (corrélation webhook)
 * @param sessionToken    jeton de session pour les SDK natifs Didit (courte durée)
 * @param verificationUrl URL hébergée du parcours de vérification
 * @param targetLevel     niveau KYC visé par cette session (1 à 4)
 */
public record KycInitDto(
        String sessionId,
        String sessionToken,
        String verificationUrl,
        int targetLevel
) {
}
