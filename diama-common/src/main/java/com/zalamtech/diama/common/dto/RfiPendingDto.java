package com.zalamtech.diama.common.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** RFI Thunes en attente de réponse, avec contexte transaction pour la file de travail ops. */
public record RfiPendingDto(
        UUID rfiId,
        UUID transactionId,
        UUID userId,
        Long thunesRfiId,
        String thunesDescription,
        String requestedInfo,
        String requestedDocs,
        BigDecimal transactionAmount,
        String transactionCurrency,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt
) {
}
