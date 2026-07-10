package com.zalamtech.diama.common.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Mise à jour partielle du profil. Tous les champs sont optionnels : un champ
 * {@code null} laisse la valeur existante inchangée.
 *
 * <p>Les contraintes {@code @Pattern}/{@code @Size} de Bean Validation acceptent
 * {@code null} par défaut — la validation ne s'applique donc qu'aux champs
 * effectivement fournis.</p>
 */
public record UserUpdateDto(

        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String lastName,

        /** Format E.164, ex. {@code +33612345678}. */
        @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "numéro de téléphone invalide (format E.164 attendu)")
        String phone,

        /** ISO 3166-1 alpha-3. */
        @Pattern(regexp = "^[A-Z]{3}$", message = "nationalité invalide (ISO 3166-1 alpha-3)")
        String nationality,

        /** ISO 3166-1 alpha-2. */
        @Pattern(regexp = "^[A-Z]{2}$", message = "pays de résidence invalide (ISO 3166-1 alpha-2)")
        String countryOfResidence,

        /** ISO 4217. */
        @Pattern(regexp = "^[A-Z]{3}$", message = "devise invalide (ISO 4217)")
        String preferredCurrency
) {
}
