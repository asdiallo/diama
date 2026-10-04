package com.zalamtech.diama.common.dto;

import java.util.UUID;

/**
 * Réponse de {@code POST /api/v1/auth/register}. Volontairement minimale :
 * l'appelant n'est pas authentifié, le profil complet se lit ensuite via
 * {@code GET /api/v1/users/me} une fois connecté.
 */
public record RegistrationResultDto(UUID id, String email) {
}
