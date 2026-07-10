package com.zalamtech.diama.common.dto;

import java.time.OffsetDateTime;

/**
 * Réponse à l'initialisation du parcours KYC : jeton Sumsub à passer au SDK
 * mobile (durée de vie 30 minutes).
 *
 * @param applicantId identifiant Sumsub de l'applicant (corrélation webhook)
 * @param sumsubToken jeton d'accès SDK Sumsub
 * @param levelName   niveau Sumsub ciblé par cette session de vérification
 * @param expiresAt   échéance du jeton
 */
public record KycInitDto(
        String applicantId,
        String sumsubToken,
        String levelName,
        OffsetDateTime expiresAt
) {
}
