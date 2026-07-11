package com.zalamtech.diama.domain.transaction;

import com.zalamtech.diama.common.dto.AdminTransactionDto;

/** Mapping {@link Transaction} → {@link AdminTransactionDto}, partagé entre les services backoffice. */
public final class AdminTransactionMapper {

    private AdminTransactionMapper() {
    }

    public static AdminTransactionDto toDto(Transaction tx) {
        return new AdminTransactionDto(
                tx.getId(), tx.getUserId(), tx.getBeneficiaryId(),
                tx.getStatus() != null ? tx.getStatus().name() : null, tx.getFailureReason(),
                tx.getSourceAmount(), tx.getSourceCurrency(), tx.getDestAmount(), tx.getDestCurrency(),
                tx.getExchangeRate(), tx.getFeeAmount(), tx.getFeeCurrency(),
                tx.getThunesQuotationId(), tx.getThunesTransactionId(), tx.getThunesExternalId(), tx.getThunesPayerId(),
                tx.getSwanTransferId(), tx.getSwanAccountId(),
                tx.getKeycloakSessionId(), tx.getInitiatorClient() != null ? tx.getInitiatorClient().name() : null,
                tx.getSenderIp(), tx.getDeviceId(),
                tx.getRefundAmount(), tx.getRefundAt(),
                tx.getCreatedAt(), tx.getUpdatedAt(), tx.getCompletedAt());
    }
}
