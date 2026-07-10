package com.zalamtech.diama.infra.swan;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Corps de la requête de virement retour Swan.
 *
 * @param originalTransferId virement Swan initial ayant collecté les fonds
 * @param iban               IBAN de destination (expéditeur à rembourser)
 * @param beneficiaryName    nom du titulaire de l'IBAN
 * @param amount             montant du remboursement
 * @param currency           devise ISO 4217
 * @param reference          libellé du virement
 */
public record SwanRefundRequest(
        @JsonProperty("original_transfer_id") String originalTransferId,
        @JsonProperty("iban") String iban,
        @JsonProperty("beneficiary_name") String beneficiaryName,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("currency") String currency,
        @JsonProperty("reference") String reference
) {
}
