package com.zalamtech.diama.common.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Ligne d'historique FSM d'une transaction. */
public record TransactionHistoryEntryDto(
        String fromStatus,
        String toStatus,
        String triggeredBy,
        UUID adminId,
        String metadata,
        OffsetDateTime changedAt
) {
}
