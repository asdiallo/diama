package com.zalamtech.diama.domain;

import com.zalamtech.diama.common.exception.ResourceNotFoundException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Moteur de frais : application de la grille EUR sur tous les paliers
 * ({@code MAX(fixe, %)} borné par plancher/plafond), bornes de tranches
 * incluses, et absence de palier.
 */
@QuarkusTest
class FeeCalculatorServiceTest {

    @Inject
    FeeCalculatorService feeCalculator;

    @Inject
    FeeTierRepository feeTiers;

    @BeforeEach
    void seedEurGrid() {
        QuarkusTransaction.requiringNew().run(() -> {
            feeTiers.delete("sourceCurrency = ?1 and destCurrency is null", "EUR");
            feeTiers.persist(tier("0", "50", "1.99", "0", null, null));            // fixe 1.99
            feeTiers.persist(tier("50.01", "200", "0", "0.0225", "2.50", null));   // 2.25 % min 2.50
            feeTiers.persist(tier("200.01", "1000", "0", "0.0200", "4.50", null));
            feeTiers.persist(tier("1000.01", null, "0", "0.0150", "20.00", "150.00")); // plafond 150
        });
    }

    @Test
    void tier1_is_flat_fixed_fee() {
        assertEquals(new BigDecimal("1.99"), fee("30"));   // MAX(1.99, 0)
    }

    @Test
    void tier1_upper_bound_is_inclusive() {
        assertEquals(new BigDecimal("1.99"), fee("50"));
        // 50.01 bascule sur le palier 2 : 50.01 * 2.25 % = 1.13 → plancher 2.50
        assertEquals(new BigDecimal("2.50"), fee("50.01"));
    }

    @Test
    void tier2_percentage_floored_by_min_fee() {
        // 100 * 2.25 % = 2.25 → sous le plancher 2.50 → 2.50
        assertEquals(new BigDecimal("2.50"), fee("100"));
        // 200 * 2.25 % = 4.50 → au-dessus du plancher → 4.50
        assertEquals(new BigDecimal("4.50"), fee("200"));
    }

    @Test
    void tier3_percentage_fee() {
        assertEquals(new BigDecimal("10.00"), fee("500"));   // 500 * 2 %
        assertEquals(new BigDecimal("20.00"), fee("1000"));  // borne haute
    }

    @Test
    void tier4_percentage_fee_capped_by_max_fee() {
        assertEquals(new BigDecimal("30.00"), fee("2000"));    // 2000 * 1.5 %
        // 15000 * 1.5 % = 225 → plafonné à 150
        assertEquals(new BigDecimal("150.00"), fee("15000"));
    }

    @Test
    void unknown_currency_has_no_tier() {
        assertThrows(ResourceNotFoundException.class,
                () -> feeCalculator.calculateFee("XXX", new BigDecimal("100"), null));
    }

    // --- Helpers ----------------------------------------------------------

    private BigDecimal fee(String amount) {
        final FeeResult r = feeCalculator.calculateFee("EUR", new BigDecimal(amount), null);
        return r.totalFee();
    }

    private static FeeTier tier(String min, String max, String fixed, String percent, String minFee, String maxFee) {
        return FeeTier.builder()
                .sourceCurrency("EUR")
                .destCurrency(null)
                .minAmount(new BigDecimal(min))
                .maxAmount(max == null ? null : new BigDecimal(max))
                .fixedFee(new BigDecimal(fixed))
                .percentFee(new BigDecimal(percent))
                .minFee(minFee == null ? null : new BigDecimal(minFee))
                .maxFee(maxFee == null ? null : new BigDecimal(maxFee))
                .isActive(Boolean.TRUE)
                .validFrom(LocalDate.now().minusDays(1))
                .build();
    }
}
