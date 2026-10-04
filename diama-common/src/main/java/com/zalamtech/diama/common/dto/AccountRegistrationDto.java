package com.zalamtech.diama.common.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload de création de compte depuis l'application mobile
 * ({@code POST /api/v1/auth/register}, endpoint public).
 *
 * <p>Contrairement à {@link UserRegistrationDto}, aucun JWT n'existe encore :
 * l'identité vient du formulaire et le compte Keycloak est créé par le backend.
 * Le mot de passe ne fait que transiter vers Keycloak — il n'est jamais
 * persisté ni journalisé côté Diama, d'où le {@code toString()} masqué.</p>
 */
public record AccountRegistrationDto(

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        /** Format E.164, ex. {@code +33612345678}. */
        @NotBlank
        @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "numéro de téléphone invalide (format E.164 attendu)")
        String phone,

        @NotBlank
        @Size(max = 100)
        String firstName,

        @NotBlank
        @Size(max = 100)
        String lastName,

        /** ISO 3166-1 alpha-2, ex. {@code FR}. */
        @NotBlank
        @Pattern(regexp = "^[A-Z]{2}$", message = "pays de résidence invalide (ISO 3166-1 alpha-2)")
        String countryOfResidence,

        /** La politique du realm Keycloak peut être plus stricte que cette borne. */
        @NotBlank
        @Size(min = 8, max = 128, message = "le mot de passe doit contenir entre 8 et 128 caractères")
        String password
) {

    /** Masque le mot de passe : un record journalisé par erreur ne doit pas le révéler. */
    @Override
    public String toString() {
        return "AccountRegistrationDto[email=" + email + ", phone=" + phone
                + ", countryOfResidence=" + countryOfResidence + ", password=***]";
    }
}
