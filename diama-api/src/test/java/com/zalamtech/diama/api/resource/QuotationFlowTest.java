package com.zalamtech.diama.api.resource;

import com.zalamtech.diama.domain.exchange.ExchangeRate;
import com.zalamtech.diama.domain.exchange.ExchangeRateRepository;
import com.zalamtech.diama.domain.exchange.ThunesPayer;
import com.zalamtech.diama.domain.exchange.ThunesPayerRepository;
import com.zalamtech.diama.domain.fee.FeeTier;
import com.zalamtech.diama.domain.fee.FeeTierRepository;
import com.zalamtech.diama.domain.kyc.KycProfile;
import com.zalamtech.diama.domain.kyc.KycProfileRepository;
import com.zalamtech.diama.domain.user.User;
import com.zalamtech.diama.domain.user.UserRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Tests bout-en-bout du corridor de tarification : {@code /api/v1/rates}
 * (public) et {@code /api/v1/quotations} (rôle {@code user}), incluant le
 * contrôle du plafond KYC.
 */
@QuarkusTest
class QuotationFlowTest {

    private static final String SUB_OK = "44444444-4444-4444-4444-444444444444";
    private static final String SUB_LIMITED = "55555555-5555-5555-5555-555555555555";

    @Inject
    UserRepository users;
    @Inject
    KycProfileRepository kycProfiles;
    @Inject
    ThunesPayerRepository payers;
    @Inject
    ExchangeRateRepository exchangeRates;
    @Inject
    FeeTierRepository feeTiers;

    @BeforeEach
    void seedEurGrid() {
        QuarkusTransaction.requiringNew().run(() -> {
            feeTiers.delete("sourceCurrency = ?1 and destCurrency is null", "EUR");
            feeTiers.persist(tier("0", "50", "1.99", "0", null));
            feeTiers.persist(tier("50.01", "200", "0", "0.0225", "2.50"));
            feeTiers.persist(tier("200.01", "1000", "0", "0.0200", "4.50"));
            feeTiers.persist(tier("1000.01", null, "0", "0.0150", "20.00"));
        });
    }

    // --- /api/v1/rates (public) ------------------------------------------

    @Test
    void current_rate_exposes_raw_and_client_rate() {
        final long payerId = setupCorridor("GN");

        given().when().get("/api/v1/rates/current?from=EUR&to=GNF&payerId=" + payerId).then()
                .statusCode(200)
                .body("sourceCurrency", is("EUR"))
                .body("destCurrency", is("GNF"))
                .body("rate", is(12000.0f))
                .body("clientRate", is(11760.0f));   // 12000 * (1 - 2 %)
    }

    @Test
    void payers_listed_by_country() {
        // Pays unique par exécution : évite le cache @CacheResult d'un run précédent.
        final String country = randomCountry();
        final long payerId = setupCorridor(country);

        given().when().get("/api/v1/rates/payers?country=" + country).then()
                .statusCode(200)
                .body("id", hasItem((int) payerId))
                .body("currency", hasItem("GNF"));
    }

    // --- /api/v1/quotations (rôle user) ----------------------------------

    @Test
    void anonymous_cannot_quote() {
        given().contentType(ContentType.JSON)
                .body("{\"sourceAmount\":100,\"sourceCurrency\":\"EUR\",\"payerId\":1}")
                .when().post("/api/v1/quotations").then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = SUB_OK, roles = "user")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = SUB_OK),
            @Claim(key = "kyc_level", value = "2"),
            @Claim(key = "azp", value = "mobile-app"),
            @Claim(key = "email", value = "quote-a@example.com"),
            @Claim(key = "phone_number", value = "+33600000444")
    })
    void quotation_locks_rate_and_computes_totals() {
        final long payerId = setupCorridor("GN");
        upsertUserWithKyc(SUB_OK, "quote-a@example.com", "+33600000444", "3000");

        given().contentType(ContentType.JSON)
                .body("{\"sourceAmount\":100.00,\"sourceCurrency\":\"EUR\",\"payerId\":%d}".formatted(payerId))
                .when().post("/api/v1/quotations").then()
                .statusCode(200)
                .body("quotationId", notNullValue())
                .body("destCurrency", is("GNF"))
                .body("clientRate", is(11760.0f))
                .body("destAmount", is(1176000.0f))    // 100 * 11760
                .body("feeAmount", is(2.5f))            // 100 * 2.25 % = 2.25 → plancher 2.50
                .body("totalDebit", is(102.5f))         // 100 + 2.50
                .body("payerName", notNullValue())
                .body("rateValidUntil", notNullValue());
    }

    @Test
    @TestSecurity(user = SUB_LIMITED, roles = "user")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = SUB_LIMITED),
            @Claim(key = "kyc_level", value = "1"),
            @Claim(key = "azp", value = "mobile-app"),
            @Claim(key = "email", value = "quote-b@example.com"),
            @Claim(key = "phone_number", value = "+33600000555")
    })
    void quotation_rejected_when_over_kyc_limit() {
        final long payerId = setupCorridor("GN");
        upsertUserWithKyc(SUB_LIMITED, "quote-b@example.com", "+33600000555", "50");

        given().contentType(ContentType.JSON)
                .body("{\"sourceAmount\":100.00,\"sourceCurrency\":\"EUR\",\"payerId\":%d}".formatted(payerId))
                .when().post("/api/v1/quotations").then()
                .statusCode(422)
                .body("code", is("MONTHLY_LIMIT_EXCEEDED"));
    }

    @Test
    @TestSecurity(user = SUB_OK, roles = "user")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = SUB_OK),
            @Claim(key = "azp", value = "mobile-app"),
            @Claim(key = "email", value = "quote-a@example.com"),
            @Claim(key = "phone_number", value = "+33600000444")
    })
    void quotation_request_is_validated() {
        given().contentType(ContentType.JSON)
                .body("{\"sourceAmount\":-5,\"sourceCurrency\":\"euro\",\"payerId\":0}")
                .when().post("/api/v1/quotations").then()
                .statusCode(400)
                .body("code", is("VALIDATION_ERROR"));
    }

    // --- Helpers ----------------------------------------------------------

    /** Crée un payer GNF + un taux EUR→GNF (12000) et renvoie l'id du payer. */
    private long setupCorridor(String country) {
        final long payerId = ThreadLocalRandom.current().nextLong(1_000_000, 9_000_000);
        QuarkusTransaction.requiringNew().run(() -> {
            payers.persist(ThunesPayer.builder()
                    .id(payerId).name("Orange Money " + payerId)
                    .countryIso(country).currency("GNF").serviceType("MobileWallet")
                    .isActive(Boolean.TRUE).build());
            exchangeRates.persist(ExchangeRate.builder()
                    .sourceCurrency("EUR").destCurrency("GNF").thunesPayerId(payerId)
                    .rate(new BigDecimal("12000.000000")).effectiveDate(LocalDate.now())
                    .isActive(Boolean.TRUE).build());
        });
        return payerId;
    }

    private void upsertUserWithKyc(String sub, String email, String phone, String limit) {
        QuarkusTransaction.requiringNew().run(() -> {
            final User user = users.findByKeycloakId(sub).orElseGet(() -> {
                final User u = User.builder()
                        .keycloakId(sub).email(email).phone(phone)
                        .firstName("Test").lastName("User").countryOfResidence("FR").build();
                users.persist(u);
                return u;
            });
            final LocalDate nextReset = LocalDate.now().with(TemporalAdjusters.firstDayOfNextMonth());
            final KycProfile kp = kycProfiles.findByUserId(user.getId()).orElse(null);
            if (kp == null) {
                kycProfiles.persist(KycProfile.builder()
                        .userId(user.getId()).kycLevel((short) 2)
                        .monthlyLimit(new BigDecimal(limit)).monthlyUsed(BigDecimal.ZERO)
                        .limitResetDate(nextReset).build());
            } else {
                kp.setMonthlyLimit(new BigDecimal(limit));
                kp.setMonthlyUsed(BigDecimal.ZERO);
                kp.setLimitResetDate(nextReset);
            }
        });
    }

    private static String randomCountry() {
        final int a = ThreadLocalRandom.current().nextInt(26);
        final int b = ThreadLocalRandom.current().nextInt(26);
        return "" + (char) ('A' + a) + (char) ('A' + b);
    }

    private static FeeTier tier(String min, String max, String fixed, String percent, String minFee) {
        return FeeTier.builder()
                .sourceCurrency("EUR").destCurrency(null)
                .minAmount(new BigDecimal(min))
                .maxAmount(max == null ? null : new BigDecimal(max))
                .fixedFee(new BigDecimal(fixed))
                .percentFee(new BigDecimal(percent))
                .minFee(minFee == null ? null : new BigDecimal(minFee))
                .isActive(Boolean.TRUE)
                .validFrom(LocalDate.now().minusDays(1))
                .build();
    }
}
