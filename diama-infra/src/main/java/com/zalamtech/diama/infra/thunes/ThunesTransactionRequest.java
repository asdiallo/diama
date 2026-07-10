package com.zalamtech.diama.infra.thunes;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Corps de {@code POST /v2/money-transfer/quotations/{id}/transactions} :
 * confirme et exécute le payout à partir d'un devis Thunes.
 *
 * @param externalId            identifiant idempotent côté Diama (= celui du devis)
 * @param sender                émetteur
 * @param beneficiary           destinataire
 * @param creditPartyIdentifier coordonnées de crédit (mobile money / banque)
 * @param purposeOfRemittance   motif du transfert (ex. {@code FAMILY_SUPPORT})
 * @param sourceOfFunds         origine des fonds (ex. {@code SALARY})
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ThunesTransactionRequest(
        @JsonProperty("external_id") String externalId,
        @JsonProperty("sender") ThunesSender sender,
        @JsonProperty("beneficiary") ThunesBeneficiary beneficiary,
        @JsonProperty("credit_party_identifier") ThunesCreditPartyIdentifier creditPartyIdentifier,
        @JsonProperty("purpose_of_remittance") String purposeOfRemittance,
        @JsonProperty("source_of_funds") String sourceOfFunds
) {
}
