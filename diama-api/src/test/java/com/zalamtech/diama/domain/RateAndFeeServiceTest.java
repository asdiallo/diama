package com.zalamtech.diama.domain;

import com.zalamtech.diama.common.exception.ResourceNotFoundException;
import com.zalamtech.diama.domain.exchange.ExchangeRate;
import com.zalamtech.diama.domain.exchange.ExchangeRateRepository;
import com.zalamtech.diama.domain.exchange.ExchangeRateService;
import com.zalamtech.diama.domain.exchange.ThunesPayer;
import com.zalamtech.diama.domain.exchange.ThunesPayerRepository;
import com.zalamtech.diama.domain.fee.FeeCalculatorService;
import com.zalamtech.diama.domain.fee.FeeResult;
import com.zalamtech.diama.domain.fee.FeeTier;
import com.zalamtech.diama.domain.fee.FeeTierRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires (déterministes) du moteur de frais et du cache de taux :
 * <ul>
 *   <li>application de la grille EUR (MAX + plancher/plafond) ;</li>
 *   <li>lecture Redis-first / repli PostgreSQL et arrondi du taux client.</li>
 * </ul>
 */
@QuarkusTest
class RateAndFeeServiceTest {

    @Inject
    FeeCalculatorService feeCalculator;

    @Inject
    FeeTierRepository feeTiers;

    @Inject
    ExchangeRateService exchangeRateService;

    @Inject
    ExchangeRateRepository exchangeRates;

    @Inject
    ThunesPayerRepository payers;

    @BeforeEach
    void seedEurGrid() {
        QuarkusTransaction.requiringNew().run(() -> {
            feeTiers.delete("sourceCurrency = ?1 and destCurrency is null", "EUR");
            feeTiers.persist(tier("0", "50", "1.99", "0", null));         // fixe 1.99
            feeTiers.persist(tier("50.01", "200", "0", "0.0225", "2.50")); // 2.25 % min 2.50
            feeTiers.persist(tier("200.01", "1000", "0", "0.0200", "4.50"));
            feeTiers.persist(tier("1000.01", null, "0", "0.0150", "20.00"));
        });
    }

    // --- Grille de frais EUR ---------------------------------------------

    @Test
    void tier1_is_flat_fixed_fee() {
        assertEquals(new BigDecimal("1.99"), fee("30"));   // MAX(1.99, 0)
    }

    @Test
    void tier2_percentage_floored_by_min_fee() {
        // 100 * 2.25 % = 2.25 → sous le plancher 2.50 → 2.50
        assertEquals(new BigDecimal("2.50"), fee("100"));
        // 200 * 2.25 % = 4.50 → au-dessus du plancher → 4.50
        assertEquals(new BigDecimal("4.50"), fee("200"));
    }

    @Test
    void tier3_and_tier4_percentage_fees() {
        assertEquals(new BigDecimal("10.00"), fee("500"));   // 500 * 2 %
        assertEquals(new BigDecimal("20.00"), fee("1000"));  // borne haute tier3
        assertEquals(new BigDecimal("30.00"), fee("2000"));  // 2000 * 1.5 %
    }

    @Test
    void unknown_currency_has_no_tier() {
        assertThrows(ResourceNotFoundException.class,
                () -> feeCalculator.calculateFee("XXX", new BigDecimal("100"), null));
    }

    // --- Cache de taux ----------------------------------------------------

    @Test
    void active_rate_reads_db_then_caches_in_redis() {
        final long payerId = ThreadLocalRandom.current().nextLong(1_000_000, 9_000_000);
        final LocalDate today = LocalDate.now();

        QuarkusTransaction.requiringNew().run(() -> {
            payers.persist(payer(payerId, "GN", "GNF"));
            exchangeRates.persist(ExchangeRate.builder()
                    .sourceCurrency("EUR").destCurrency("GNF").thunesPayerId(payerId)
                    .rate(new BigDecimal("12000.000000"))            // spread 2 % par défaut
                    .effectiveDate(today).isActive(Boolean.TRUE)
                    .build());
        });

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

    private BigDecimal fee(String amount) {
        final FeeResult r = feeCalculator.calculateFee("EUR", new BigDecimal(amount), null);
        return r.totalFee();
    }

    private static FeeTier tier(String min, String max, String fixed, String percent, String minFee) {
        return FeeTier.builder()
                .sourceCurrency("EUR")
                .destCurrency(null)
                .minAmount(new BigDecimal(min))
                .maxAmount(max == null ? null : new BigDecimal(max))
                .fixedFee(new BigDecimal(fixed))
                .percentFee(new BigDecimal(percent))
                .minFee(minFee == null ? null : new BigDecimal(minFee))
                .isActive(Boolean.TRUE)
                .validFrom(LocalDate.now().minusDays(1))
                .build();
    }

    private static ThunesPayer payer(long id, String country, String currency) {
        return ThunesPayer.builder()
                .id(id).name("Test Payer " + id).countryIso(country).currency(currency)
                .serviceType("MobileWallet").isActive(Boolean.TRUE)
                .build();
    }
}
