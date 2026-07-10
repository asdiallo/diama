package com.zalamtech.diama.common.exception;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Corps de réponse d'erreur uniforme renvoyé par les {@code ExceptionMapper}.
 *
 * @param code       code d'erreur stable ({@code RESOURCE_NOT_FOUND}, {@code VALIDATION_ERROR}, …)
 * @param message    message lisible (localisé côté API)
 * @param violations détails champ par champ (vide hors erreurs de validation)
 * @param timestamp  instant de l'erreur (UTC)
 */
public record ApiError(
        String code,
        String message,
        List<Violation> violations,
        OffsetDateTime timestamp
) {

    /** Violation d'une contrainte sur un champ précis. */
    public record Violation(String field, String message) {
    }

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, List.of(), OffsetDateTime.now(ZoneOffset.UTC));
    }

    public static ApiError of(String code, String message, List<Violation> violations) {
        return new ApiError(code, message, violations, OffsetDateTime.now(ZoneOffset.UTC));
    }
}
