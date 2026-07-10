package com.zalamtech.diama.api.scheduler;

import com.zalamtech.diama.domain.exchange.ExchangeRateService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Déclencheur du rafraîchissement quotidien des taux de change Thunes.
 * S'exécute chaque jour à 06:00 UTC.
 */
@Slf4j
@ApplicationScoped
public class RateRefreshScheduler {

    @Inject
    ExchangeRateService exchangeRates;

    /**
     * Refresh quotidien à 06:00 UTC. {@code concurrentExecution = SKIP} évite un
     * chevauchement si une exécution précédente déborde.
     */
    @Scheduled(cron = "0 0 6 * * ?", timeZone = "UTC",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void refreshDailyRates() {
        try {
            final int updated = exchangeRates.refreshRatesFromThunes();
            log.info("Refresh quotidien des taux terminé : {} corridors", updated);
        } catch (RuntimeException e) {
            // Ne jamais laisser une exception s'échapper du scheduler (retry le lendemain).
            log.error("Échec du refresh quotidien des taux Thunes", e);
        }
    }
}
