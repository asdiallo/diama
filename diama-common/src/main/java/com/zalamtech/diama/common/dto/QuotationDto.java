package com.zalamtech.diama.common.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Devis de transfert avec taux verrouillé. Renvoyé au client mobile avant
 * confirmation d'une transaction ; référencé par {@code quotationId} (mis en
 * cache Redis 15 min).
 *
 * @param quotationId       identifiant du devis (clé de cache Redis)
 * @param sourceAmount      montant envoyé (devise source)
 * @param sourceCurrency    devise source (ex. {@code EUR})
 * @param destAmount        montant reçu (devise destination) = {@code sourceAmount * clientRate}
 * @param destCurrency      devise destination (ex. {@code GNF})
 * @param exchangeRate      taux brut (avant marge), à titre indicatif
 * @param clientRate        taux réellement appliqué
 * @param feeAmount         frais de transfert (devise source)
 * @param totalDebit        total débité = {@code sourceAmount + feeAmount}
 * @param payerName         nom de l'opérateur de payout
 * @param estimatedDelivery délai de livraison estimé (libellé)
 * @param rateValidUntil    expiration du taux verrouillé (15 min)
 */
public record QuotationDto(
        UUID quotationId,
        BigDecimal sourceAmount,
        String sourceCurrency,
        BigDecimal destAmount,
        String destCurrency,
        BigDecimal exchangeRate,
        BigDecimal clientRate,
        BigDecimal feeAmount,
        BigDecimal totalDebit,
        String payerName,
        String estimatedDelivery,
        OffsetDateTime rateValidUntil
) {
}
