package com.zalamtech.diama.domain;

import com.zalamtech.diama.domain.exchange.ExchangeRate;
import com.zalamtech.diama.domain.exchange.ExchangeRateRepository;
import com.zalamtech.diama.domain.exchange.ExchangeRateService;
import com.zalamtech.diama.domain.exchange.ThunesPayer;
import com.zalamtech.diama.domain.exchange.ThunesPayerRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cache de taux Redis-first : miss → lecture PostgreSQL + mise en cache,
 * hit → lecture sans repasser par la base.
 */
@QuarkusTest
class ExchangeRateServiceTest {

    @Inject
    ExchangeRateService exchangeRateService;

    @Inject
    ExchangeRateRepository exchangeRates;

    @Inject
    ThunesPayerRepository payers;

    @Test
    void cache_miss_reads_db_then_caches_in_redis() {
        final long payerId = ThreadLocalRandom.current().nextLong(1_000_000, 9_000_000);
        final LocalDate today = LocalDate.now();
        seedRate(payerId, "GNF", "12000.000000", today);

        final ExchangeRate rate = exchangeRateService.getActiveRate("EUR", "GNF", payerId);
        // client_rate = 12000 * (1 - 0.02) = 11760 (colonne générée en base)
        assertEquals(0, new BigDecimal("11760.000000").compareTo(rate.getClientRate()));

        // Le taux est désormais en cache sous la clé du jour.
        final String key = "rate:EUR:GNF:%s:%s".formatted(payerId, today);
        final Optional<ExchangeRate> cached = exchangeRateService.getFromRedis(key);
        assertTrue(cached.isPresent(), "le taux doit être mis en cache Redis après lecture DB");
        assertEquals(0, new BigDecimal("12000.000000").compareTo(cached.get().getRate()));
    }

    @Test
    void cache_hit_bypasses_database() {
        final long payerId = ThreadLocalRandom.current().nextLong(1_000_000, 9_000_000);
        final LocalDate today = LocalDate.now();

        // Taux mis en cache uniquement (aucune ligne exchange_rates en base) :
        // un getActiveRate qui aboutit prouve que le hit Redis suffit.
        final ExchangeRate cachedOnly = ExchangeRate.builder()
                .sourceCurrency("EUR").destCurrency("XOF").thunesPayerId(payerId)
                .rate(new BigDecimal("655.957000")).effectiveDate(today).isActive(Boolean.TRUE)
                .build();
        exchangeRateService.storeInRedis(cachedOnly);

        final ExchangeRate rate = exchangeRateService.getActiveRate("EUR", "XOF", payerId);
        assertEquals(0, new BigDecimal("655.957000").compareTo(rate.getRate()));
        assertEquals("XOF", rate.getDestCurrency());
    }

    @Test
    void store_and_get_from_redis_roundtrip() {
        final long payerId = ThreadLocalRandom.current().nextLong(1_000_000, 9_000_000);
        final LocalDate today = LocalDate.now();
        final ExchangeRate rate = ExchangeRate.builder()
                .sourceCurrency("EUR").destCurrency("XOF").thunesPayerId(payerId)
                .rate(new BigDecimal("655.957000")).effectiveDate(today).isActive(Boolean.TRUE)
                .build();

        exchangeRateService.storeInRedis(rate);

        final String key = "rate:EUR:XOF:%s:%s".formatted(payerId, today);
        final Optional<ExchangeRate> back = exchangeRateService.getFromRedis(key);
        assertTrue(back.isPresent());
        assertEquals(0, new BigDecimal("655.957000").compareTo(back.get().getRate()));
        assertEquals("XOF", back.get().getDestCurrency());
    }

    // --- Helpers ----------------------------------------------------------

    private void seedRate(long payerId, String destCurrency, String rate, LocalDate date) {
        QuarkusTransaction.requiringNew().run(() -> {
            payers.persist(ThunesPayer.builder()
                    .id(payerId).name("Test Payer " + payerId).countryIso("GN").currency(destCurrency)
                    .serviceType("MobileWallet").isActive(Boolean.TRUE)
                    .build());
            exchangeRates.persist(ExchangeRate.builder()
                    .sourceCurrency("EUR").destCurrency(destCurrency).thunesPayerId(payerId)
                    .rate(new BigDecimal(rate)).effectiveDate(date).isActive(Boolean.TRUE)
                    .build());
        });
    }
}
