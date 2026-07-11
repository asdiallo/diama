package com.zalamtech.diama.common.dto;

import java.util.List;

/** Détail backoffice d'une transaction : données + historique complet des transitions FSM. */
public record TransactionDetailDto(
        AdminTransactionDto transaction,
        List<TransactionHistoryEntryDto> history
) {
}
