package com.zalamtech.diama.domain;

import com.zalamtech.diama.common.dto.CreateTransferRequest;
import com.zalamtech.diama.common.dto.QuotationDto;
import com.zalamtech.diama.common.exception.QuotationExpiredException;
import com.zalamtech.diama.domain.exchange.ExchangeRate;
import com.zalamtech.diama.domain.exchange.ExchangeRateRepository;
import com.zalamtech.diama.domain.exchange.ThunesPayer;
import com.zalamtech.diama.domain.exchange.ThunesPayerRepository;
import com.zalamtech.diama.domain.fee.FeeTier;
import com.zalamtech.diama.domain.fee.FeeTierRepository;
import com.zalamtech.diama.domain.kyc.KycProfile;
import com.zalamtech.diama.domain.kyc.KycProfileRepository;
import com.zalamtech.diama.domain.quotation.QuotationService;
import com.zalamtech.diama.domain.transaction.TransferService;
import com.zalamtech.diama.domain.user.User;
import com.zalamtech.diama.domain.user.UserBeneficiary;
import com.zalamtech.diama.domain.user.UserBeneficiaryRepository;
import com.zalamtech.diama.domain.user.UserRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verrouillage de devis : TTL Redis de 15 minutes, consommation unique
 * (getdel) et rejet d'un devis expiré / inexistant.
 */
@QuarkusTest
class QuotationServiceTest {

    private static final Duration EXPECTED_TTL = Duration.ofMinutes(15);

    @Inject
    QuotationService quotations;

    @Inject
    TransferService transfers;

    @Inject
    RedisDataSource redis;

    @Inject
    UserRepository users;
    @Inject
    KycProfileRepository kycProfiles;
    @Inject
    UserBeneficiaryRepository beneficiaries;
    @Inject
    ThunesPayerRepository payers;
    @Inject
    ExchangeRateRepository exchangeRates;
    @Inject
    FeeTierRepository feeTiers;

    private UUID userId;
    private UUID beneficiaryId;
    private long payerId;

    @BeforeEach
    void seed() {
        final String uniq = UUID.randomUUID().toString().substring(0, 8);
        payerId = ThreadLocalRandom.current().nextLong(1_000_000, 9_000_000);

        QuarkusTransaction.requiringNew().run(() -> {
            feeTiers.delete("sourceCurrency = ?1 and destCurrency is null", "EUR");
            feeTiers.persist(FeeTier.builder()
                    .sourceCurrency("EUR").minAmount(BigDecimal.ZERO).maxAmount(null)
                    .fixedFee(new BigDecimal("1.99")).percentFee(BigDecimal.ZERO)
                    .isActive(Boolean.TRUE).validFrom(LocalDate.now().minusDays(1))
                    .build());

            payers.persist(ThunesPayer.builder()
                    .id(payerId).name("Quot Payer").countryIso("GN").currency("GNF")
                    .serviceType("MobileWallet").isActive(Boolean.TRUE)
                    .build());
            exchangeRates.persist(ExchangeRate.builder()
                    .sourceCurrency("EUR").destCurrency("GNF").thunesPayerId(payerId)
                    .rate(new BigDecimal("12000.000000")).effectiveDate(LocalDate.now())
                    .isActive(Boolean.TRUE)
                    .build());

            final User user = User.builder()
                    .keycloakId("kc-quot-" + uniq)
                    .email("quot-" + uniq + "@example.com")
                    .phone("+3361" + Long.toString(System.nanoTime() % 100000000L))
                    .firstName("Aïssatou").lastName("Bah").countryOfResidence("FR")
                    .build();
            users.persist(user);
            userId = user.getId();

            kycProfiles.persist(KycProfile.builder()
                    .userId(userId).kycLevel((short) 2)
                    .monthlyLimit(new BigDecimal("3000")).monthlyUsed(BigDecimal.ZERO)
                    .limitResetDate(LocalDate.now().plusMonths(1))
                    .levelGrantedAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .build());

            final UserBeneficiary beneficiary = UserBeneficiary.builder()
                    .userId(userId).firstName("Mamadou").lastName("Diallo")
                    .phoneMsisdn("+224620000000").countryIso("GN").currency("GNF")
                    .thunesPayerId(payerId).payerService("MobileWallet")
                    .isActive(Boolean.TRUE)
                    .build();
            beneficiaries.persist(beneficiary);
            beneficiaryId = beneficiary.getId();
        });
    }

    @Test
    void quotation_is_locked_in_redis_for_fifteen_minutes() {
        final QuotationDto quotation = quotations.buildQuotation(
                userId, new BigDecimal("100.00"), "EUR", payerId);

        final String key = "quot:%s:%s".formatted(userId, quotation.quotationId());
        final long ttl = redis.key(String.class).ttl(key);

        assertTrue(ttl > 0, "le devis doit être en cache avec un TTL");
        assertTrue(ttl <= EXPECTED_TTL.toSeconds(), "TTL au plus 15 min, obtenu : " + ttl);
        assertTrue(ttl > EXPECTED_TTL.toSeconds() - 30, "TTL proche de 15 min, obtenu : " + ttl);

        // L'échéance annoncée au client correspond au TTL.
        assertTrue(quotation.rateValidUntil().isAfter(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(14)));
    }

    @Test
    void expired_quotation_cannot_be_consumed() {
        final QuotationDto quotation = quotations.buildQuotation(
                userId, new BigDecimal("100.00"), "EUR", payerId);

        // Simule l'expiration du TTL (15 min) : la clé disparaît de Redis.
        final String key = "quot:%s:%s".formatted(userId, quotation.quotationId());
        redis.key(String.class).del(key);

        assertThrows(QuotationExpiredException.class,
                () -> transfers.initiateTransfer(userId, new CreateTransferRequest(quotation.quotationId(), beneficiaryId)));
    }

    @Test
    void quotation_is_single_use() {
        final QuotationDto quotation = quotations.buildQuotation(
                userId, new BigDecimal("100.00"), "EUR", payerId);
        final CreateTransferRequest request = new CreateTransferRequest(quotation.quotationId(), beneficiaryId);

        transfers.initiateTransfer(userId, request);

        // Deuxième consommation du même devis : refusée (getdel atomique).
        assertThrows(QuotationExpiredException.class, () -> transfers.initiateTransfer(userId, request));
    }

    @Test
    void unknown_quotation_is_rejected() {
        assertThrows(QuotationExpiredException.class,
                () -> transfers.initiateTransfer(userId, new CreateTransferRequest(UUID.randomUUID(), beneficiaryId)));
    }

    @Test
    void quotation_applies_client_rate_and_fees() {
        final QuotationDto quotation = quotations.buildQuotation(
                userId, new BigDecimal("100.00"), "EUR", payerId);

        // 100 * 12000 * (1 - 2 %) = 1 176 000 GNF ; frais fixes 1.99 → débit 101.99
        assertEquals(0, new BigDecimal("1176000.00").compareTo(quotation.destAmount()));
        assertEquals(0, new BigDecimal("1.99").compareTo(quotation.feeAmount()));
        assertEquals(0, new BigDecimal("101.99").compareTo(quotation.totalDebit()));
    }
}
