package com.zalamtech.diama.common.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Corps de la demande de devis ({@code POST /api/v1/quotations}).
 *
 * @param sourceAmount   montant à envoyer (&gt; 0, 2 décimales max)
 * @param sourceCurrency devise source (ISO 4217)
 * @param payerId        payer Thunes cible (détermine devise et pays de destination)
 */
public record QuotationRequest(

        @NotNull
        @DecimalMin(value = "0.01", message = "le montant doit être strictement positif")
        @Digits(integer = 16, fraction = 2, message = "montant invalide (2 décimales max)")
        BigDecimal sourceAmount,

        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$", message = "devise invalide (ISO 4217)")
        String sourceCurrency,

        @NotNull
        @Positive
        Long payerId
) {
}
