package com.zalamtech.diama.common.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Vue publique du profil utilisateur.
 *
 * <p><b>N'expose jamais</b> le {@code keycloak_id} ni le {@code swan_account_id}
 * (identifiants internes / techniques). Le {@code status} est renvoyé sous forme
 * de chaîne pour découpler le contrat public de l'enum du domaine.</p>
 */
public record UserProfileDto(
        UUID id,
        String email,
        String phone,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String nationality,
        String countryOfResidence,
        String preferredCurrency,
        String status,
        String swanIban,
        String referralCode,
        OffsetDateTime createdAt
) {
}
