package com.zalamtech.diama.infra.thunes;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Coordonnées de crédit du bénéficiaire (bloc {@code credit_party_identifier}).
 * Selon le type de payout : {@code msisdn} (mobile money) ou compte bancaire
 * ({@code bank_account_number} + {@code swift_bic_code} / {@code iban}).
 *
 * @param msisdn            numéro mobile money (E.164) pour un payout MobileWallet
 * @param bankAccountNumber numéro de compte pour un payout BankAccount
 * @param swiftBicCode      code SWIFT/BIC de la banque destinataire
 * @param iban              IBAN du bénéficiaire, le cas échéant
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ThunesCreditPartyIdentifier(
        @JsonProperty("msisdn") String msisdn,
        @JsonProperty("bank_account_number") String bankAccountNumber,
        @JsonProperty("swift_bic_code") String swiftBicCode,
        @JsonProperty("iban") String iban
) {
}
