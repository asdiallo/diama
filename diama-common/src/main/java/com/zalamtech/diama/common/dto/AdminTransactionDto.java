package com.zalamtech.diama.common.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Vue backoffice d'une transaction — plus riche que {@link TransferDto} (vue
 * mobile) : expose les références partenaires, le contexte de session et les
 * champs de remboursement, utiles à l'investigation ops.
 */
public record AdminTransactionDto(
        UUID id,
        UUID userId,
        UUID beneficiaryId,
        String status,
        String failureReason,
        BigDecimal sourceAmount,
        String sourceCurrency,
        BigDecimal destAmount,
        String destCurrency,
        BigDecimal exchangeRate,
        BigDecimal feeAmount,
        String feeCurrency,
        Long thunesQuotationId,
        Long thunesTransactionId,
        String thunesExternalId,
        Long thunesPayerId,
        String swanTransferId,
        String swanAccountId,
        String keycloakSessionId,
        String initiatorClient,
        String senderIp,
        UUID deviceId,
        BigDecimal refundAmount,
        OffsetDateTime refundAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt
) {
}
