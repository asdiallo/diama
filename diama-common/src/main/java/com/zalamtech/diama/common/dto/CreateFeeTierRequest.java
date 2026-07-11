package com.zalamtech.diama.common.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Création (ou remplacement — {@code PUT /fees/{id}}) d'un palier de frais.
 *
 * @param sourceCurrency devise source (ISO 4217)
 * @param destCurrency   devise destination, {@code null} pour un palier générique
 * @param minAmount      borne basse de la tranche (incluse)
 * @param maxAmount      borne haute de la tranche (incluse), {@code null} = illimité
 * @param fixedFee       frais fixe
 * @param percentFee     frais proportionnel (fraction, ex. {@code 0.01} pour 1 %)
 * @param minFee         plancher de frais, {@code null} = aucun
 * @param maxFee         plafond de frais, {@code null} = aucun
 * @param validFrom      date d'effet, {@code null} = aujourd'hui
 */
public record CreateFeeTierRequest(
        @NotBlank @Size(min = 3, max = 3) String sourceCurrency,
        @Size(min = 3, max = 3) String destCurrency,
        @NotNull @DecimalMin("0.0") BigDecimal minAmount,
        BigDecimal maxAmount,
        @NotNull @DecimalMin("0.0") BigDecimal fixedFee,
        @NotNull @DecimalMin("0.0") BigDecimal percentFee,
        BigDecimal minFee,
        BigDecimal maxFee,
        LocalDate validFrom
) {
}
