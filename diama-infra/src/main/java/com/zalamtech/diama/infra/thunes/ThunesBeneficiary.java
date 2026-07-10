package com.zalamtech.diama.infra.thunes;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Destinataire d'une transaction Thunes (bloc {@code beneficiary}).
 *
 * @param lastname       nom de famille
 * @param firstname      prénom
 * @param countryIsoCode pays du bénéficiaire (ISO 3166-1 alpha-2)
 * @param msisdn         téléphone au format E.164, le cas échéant
 * @param email          adresse e-mail, le cas échéant
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ThunesBeneficiary(
        @JsonProperty("lastname") String lastname,
        @JsonProperty("firstname") String firstname,
        @JsonProperty("country_iso_code") String countryIsoCode,
        @JsonProperty("msisdn") String msisdn,
        @JsonProperty("email") String email
) {
}
