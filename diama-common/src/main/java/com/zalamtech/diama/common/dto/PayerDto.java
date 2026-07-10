package com.zalamtech.diama.common.dto;

import java.math.BigDecimal;

/**
 * Opérateur de payout Thunes (mobile money, banque, cash) proposé sur un pays.
 *
 * @param id          identifiant Thunes natif
 * @param name        nom commercial de l'opérateur
 * @param countryIso  pays desservi (ISO 3166-1 alpha-2)
 * @param currency    devise de payout (ISO 4217)
 * @param serviceType {@code MobileWallet} | {@code BankAccount} | {@code CashPickup}
 * @param minAmount   montant minimum accepté (devise source), {@code null} si non borné
 * @param maxAmount   montant maximum accepté (devise source), {@code null} si non borné
 */
public record PayerDto(
        Long id,
        String name,
        String countryIso,
        String currency,
        String serviceType,
        BigDecimal minAmount,
        BigDecimal maxAmount
) {
}
