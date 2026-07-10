package com.zalamtech.diama.common.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Payload d'inscription — complète le profil applicatif après création du compte
 * Keycloak. L'identité (email / téléphone) provient du JWT ; ce DTO porte les
 * données métier saisies par l'utilisateur.
 */
public record UserRegistrationDto(

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

        @Past
        LocalDate dateOfBirth,

        /** ISO 3166-1 alpha-3, ex. {@code GIN}, {@code SEN}, {@code FRA}. */
        @Pattern(regexp = "^[A-Z]{3}$", message = "nationalité invalide (ISO 3166-1 alpha-3)")
        String nationality,

        /** ISO 3166-1 alpha-2, ex. {@code FR}. */
        @NotBlank
        @Pattern(regexp = "^[A-Z]{2}$", message = "pays de résidence invalide (ISO 3166-1 alpha-2)")
        String countryOfResidence,

        /** ISO 4217, ex. {@code EUR}. */
        @Pattern(regexp = "^[A-Z]{3}$", message = "devise invalide (ISO 4217)")
        String preferredCurrency
) {
}
