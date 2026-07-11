package com.zalamtech.diama.infra.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Message du topic {@code rate-updated}, publié par {@code ExchangeRateService}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RateUpdatedEvent(
        String source,
        String dest,
        Long payerId,
        String effectiveDate
) {
}
