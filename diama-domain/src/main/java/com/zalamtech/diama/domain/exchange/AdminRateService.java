package com.zalamtech.diama.domain.exchange;

import com.zalamtech.diama.common.dto.AdminRateDto;
import com.zalamtech.diama.common.exception.ResourceNotFoundException;
import com.zalamtech.diama.domain.audit.AuditService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Consultation et pilotage backoffice des taux de change. */
@Slf4j
@ApplicationScoped
public class AdminRateService {

    @Inject
    ExchangeRateRepository exchangeRates;

    @Inject
    ThunesPayerRepository payers;

    @Inject
    ExchangeRateService exchangeRateService;

    @Inject
    AuditService audit;

    public List<AdminRateDto> listActive() {
        return exchangeRates.findAllActive().stream().map(this::toDto).toList();
    }

    public List<AdminRateDto> history(String sourceCurrency, String destCurrency, int page, int size) {
        return exchangeRates.findHistory(sourceCurrency, destCurrency, page, size).stream().map(this::toDto).toList();
    }

    public long countHistory(String sourceCurrency, String destCurrency) {
        return exchangeRates.countHistory(sourceCurrency, destCurrency);
    }

    /** Modifie la marge d'un taux ; {@code client_rate} (colonne générée) est rematérialisée avant réponse. */
    @Transactional
    public AdminRateDto updateSpread(UUID rateId, BigDecimal spreadPct, UUID adminId) {
        final ExchangeRate rate = exchangeRates.findByIdOptional(rateId)
                .orElseThrow(() -> new ResourceNotFoundException("Taux de change", rateId));
        final BigDecimal before = rate.getSpreadPct();

        rate.setSpreadPct(spreadPct);
        rate.setUpdatedBy(adminId);
        exchangeRates.flush(); // matérialise client_rate (colonne générée) avant lecture

        exchangeRateService.storeInRedis(rate);
        exchangeRateService.enqueueRateUpdated(rate);

        audit.log(null, null, adminId, "RATE_SPREAD_UPDATED", "EXCHANGE_RATE", rateId,
                Map.of("spreadPct", before), Map.of("spreadPct", spreadPct));

        log.info("Marge modifiée : rate={} {} → {} (admin={})", rateId, before, spreadPct, adminId);
        return toDto(rate);
    }

    /** Refresh manuel depuis Thunes (identique au job quotidien, déclenché ici par un agent). */
    @Transactional
    public int refresh(UUID adminId) {
        final int updated = exchangeRateService.refreshRatesFromThunes();
        audit.log(null, null, adminId, "RATE_MANUAL_REFRESH", "EXCHANGE_RATE", null, null,
                Map.of("corridorsUpdated", updated));
        log.info("Refresh manuel des taux Thunes : {} corridors (admin={})", updated, adminId);
        return updated;
    }

    private AdminRateDto toDto(ExchangeRate rate) {
        final String payerName = rate.getThunesPayerId() == null ? null
                : payers.findByIdOptional(rate.getThunesPayerId()).map(ThunesPayer::getName).orElse(null);
        return new AdminRateDto(
                rate.getId(), rate.getSourceCurrency(), rate.getDestCurrency(), rate.getThunesPayerId(), payerName,
                rate.getRate(), rate.getSpreadPct(), rate.getClientRate(), rate.getEffectiveDate(),
                Boolean.TRUE.equals(rate.getIsActive()), rate.getUpdatedBy(), rate.getFetchedAt());
    }
}
