package com.zalamtech.diama.infra.thunes;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Émetteur d'une transaction Thunes (bloc {@code sender}).
 *
 * @param lastname          nom de famille
 * @param firstname         prénom
 * @param dateOfBirth       date de naissance (ISO-8601), requise selon le pays
 * @param countryIsoCode    pays de résidence (ISO 3166-1 alpha-2)
 * @param nationality       nationalité (ISO 3166-1 alpha-2/3), selon exigences KYC
 * @param msisdn            téléphone au format E.164
 * @param email             adresse e-mail
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ThunesSender(
        @JsonProperty("lastname") String lastname,
        @JsonProperty("firstname") String firstname,
        @JsonProperty("date_of_birth") String dateOfBirth,
        @JsonProperty("country_iso_code") String countryIsoCode,
        @JsonProperty("nationality_country_iso_code") String nationality,
        @JsonProperty("msisdn") String msisdn,
        @JsonProperty("email") String email
) {
}
