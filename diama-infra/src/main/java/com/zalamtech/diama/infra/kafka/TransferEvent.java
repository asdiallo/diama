package com.zalamtech.diama.infra.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.UUID;

/** Message du topic {@code transfer-events}, publié par {@code TransferEventPublisher} à chaque transition FSM. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransferEvent(
        UUID transactionId,
        UUID userId,
        String status,
        BigDecimal sourceAmount,
        String sourceCurrency,
        String destCurrency
) {
}
