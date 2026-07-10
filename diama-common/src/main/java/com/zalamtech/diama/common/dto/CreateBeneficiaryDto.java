package com.zalamtech.diama.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Création d'un bénéficiaire. Selon le {@code payerService} cible, le paiement
 * s'effectue sur un mobile money ({@code phoneMsisdn}) ou un compte bancaire
 * ({@code bankAccountNo} + {@code swiftBic}) — cette cohérence est vérifiée
 * côté service.
 */
public record CreateBeneficiaryDto(

        @Size(max = 100)
        String nickname,

        @NotBlank
        @Size(max = 100)
        String firstName,

        @NotBlank
        @Size(max = 100)
        String lastName,

        /** Format E.164, ex. {@code +224601234567}. */
        @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "MSISDN invalide (format E.164 attendu)")
        String phoneMsisdn,

        @Size(max = 50)
        String bankAccountNo,

        /** BIC/SWIFT : 8 ou 11 caractères. */
        @Pattern(regexp = "^[A-Za-z]{4}[A-Za-z]{2}[A-Za-z0-9]{2}([A-Za-z0-9]{3})?$",
                message = "code BIC/SWIFT invalide")
        String swiftBic,

        /** ISO 3166-1 alpha-2, ex. {@code GN}, {@code SN}. */
        @NotBlank
        @Pattern(regexp = "^[A-Z]{2}$", message = "pays invalide (ISO 3166-1 alpha-2)")
        String countryIso,

        /** ISO 4217, ex. {@code GNF}, {@code XOF}. */
        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$", message = "devise invalide (ISO 4217)")
        String currency,

        Long thunesPayerId,

        /** {@code MobileWallet} | {@code BankAccount} | {@code CashPickup}. */
        @Pattern(regexp = "^(MobileWallet|BankAccount|CashPickup)$",
                message = "service de paiement invalide")
        String payerService
) {
}
