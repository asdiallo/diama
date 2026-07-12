package com.zalamtech.diama.flow;

import com.zalamtech.diama.domain.audit.KafkaOutbox;
import com.zalamtech.diama.domain.audit.KafkaOutboxRepository;
import com.zalamtech.diama.domain.exchange.ExchangeRate;
import com.zalamtech.diama.domain.exchange.ExchangeRateRepository;
import com.zalamtech.diama.domain.exchange.ThunesPayer;
import com.zalamtech.diama.domain.exchange.ThunesPayerRepository;
import com.zalamtech.diama.domain.fee.FeeTier;
import com.zalamtech.diama.domain.fee.FeeTierRepository;
import com.zalamtech.diama.domain.kyc.KycProfile;
import com.zalamtech.diama.domain.kyc.KycProfileRepository;
import com.zalamtech.diama.domain.transaction.ThunesTransferGateway;
import com.zalamtech.diama.domain.transaction.ThunesTransferResult;
import com.zalamtech.diama.domain.transaction.Transaction;
import com.zalamtech.diama.domain.transaction.TransactionRepository;
import com.zalamtech.diama.domain.transaction.TxStatus;
import com.zalamtech.diama.domain.user.User;
import com.zalamtech.diama.domain.user.UserBeneficiary;
import com.zalamtech.diama.domain.user.UserBeneficiaryRepository;
import com.zalamtech.diama.domain.user.UserRepository;
import com.zalamtech.diama.infra.kafka.SwanWebhookConsumer;
import com.zalamtech.diama.infra.kafka.ThunesCallbackConsumer;
import com.zalamtech.diama.infra.kafka.TransferEventConsumer;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flux complet d'un transfert : inscription (sync JWT) → devis → initiation
 * → collecte Swan (webhook BOOKED) → confirmation Thunes → PROCESSING →
 * DELIVERED, avec mise à jour du plafond mensuel consommé.
 *
 * <p>Le broker Kafka est absent en test : chaque saut webhook → outbox →
 * consumer est rejoué en invoquant le consumer directement avec le payload
 * réellement enfilé dans {@code kafka_outbox} (schedulers désactivés, les
 * entrées restent {@code PENDING}). Les APIs partenaires sont substituées au
 * niveau des ports du domaine ({@code @InjectMock}).</p>
 */
@QuarkusTest
class TransferFlowIT {

    private static final String SUB = "77777777-7777-4777-8777-777777777777";
    private static final String IBAN = "FR7612345678901234567890777";

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
    @Inject
    TransactionRepository transactions;
    @Inject
    KafkaOutboxRepository outbox;

    @Inject
    SwanWebhookConsumer swanWebhookConsumer;
    @Inject
    ThunesCallbackConsumer thunesCallbackConsumer;
    @Inject
    TransferEventConsumer transferEventConsumer;

    @InjectMock
    ThunesTransferGateway thunesGateway;

    private long payerId;

    @BeforeEach
    void seedReferenceData() {
        payerId = ThreadLocalRandom.current().nextLong(1_000_000, 9_000_000);
        QuarkusTransaction.requiringNew().run(() -> {
            feeTiers.delete("sourceCurrency = ?1 and destCurrency is null", "EUR");
            feeTiers.persist(FeeTier.builder()
                    .sourceCurrency("EUR").minAmount(BigDecimal.ZERO)
                    .fixedFee(new BigDecimal("1.99")).percentFee(BigDecimal.ZERO)
                    .isActive(Boolean.TRUE).validFrom(LocalDate.now().minusDays(1))
                    .build());
            payers.persist(ThunesPayer.builder()
                    .id(payerId).name("Orange Money GN").countryIso("GN").currency("GNF")
                    .serviceType("MobileWallet").isActive(Boolean.TRUE)
                    .build());
            exchangeRates.persist(ExchangeRate.builder()
                    .sourceCurrency("EUR").destCurrency("GNF").thunesPayerId(payerId)
                    .rate(new BigDecimal("12000.000000")).effectiveDate(LocalDate.now())
                    .isActive(Boolean.TRUE)
                    .build());
        });
    }

    @Test
    @TestSecurity(user = SUB, roles = "user")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = SUB),
            @Claim(key = "email", value = "flow-transfer@example.com"),
            @Claim(key = "phone_number", value = "+33612347777"),
            @Claim(key = "given_name", value = "Fanta"),
            @Claim(key = "family_name", value = "Camara")
    })
    void full_transfer_flow_from_registration_to_delivered() {
        // --- 1. Inscription : première requête authentifiée → création du user.
        given().when().get("/api/mobile/me").then().statusCode(200);

        final UUID userId = users.findByKeycloakId(SUB).orElseThrow().getId();
        final UUID beneficiaryId = QuarkusTransaction.requiringNew().call(() -> {
            // Ré-exécutions sur conteneur DevServices réutilisé : upsert du profil
            // KYC et neutralisation des transactions INITIATED d'un run précédent
            // (le webhook Swan apparie la plus ancienne INITIATED du même montant).
            transactions.update("status = ?1 where userId = ?2 and status = ?3",
                    TxStatus.CANCELLED, userId, TxStatus.INITIATED);

            // KYC niveau 1 + IBAN Swan dédié (corrélation du webhook de collecte).
            final User user = users.findByIdOptional(userId).orElseThrow();
            user.setSwanIban(IBAN);
            final KycProfile kyc = kycProfiles.findByUserId(userId).orElseGet(() -> {
                final KycProfile created = KycProfile.builder().userId(userId).build();
                kycProfiles.persist(created);
                return created;
            });
            kyc.setKycLevel((short) 1);
            kyc.setMonthlyLimit(new BigDecimal("300"));
            kyc.setMonthlyUsed(BigDecimal.ZERO);
            kyc.setLimitResetDate(LocalDate.now().plusMonths(1));

            final UserBeneficiary beneficiary = UserBeneficiary.builder()
                    .userId(userId).firstName("Sékou").lastName("Touré")
                    .phoneMsisdn("+224621111111").countryIso("GN").currency("GNF")
                    .thunesPayerId(payerId).payerService("MobileWallet")
                    .isActive(Boolean.TRUE)
                    .build();
            beneficiaries.persist(beneficiary);
            return beneficiary.getId();
        });

        // --- 2. Devis verrouillé (100 EUR → GNF).
        final String quotationId = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"sourceAmount": 100.00, "sourceCurrency": "EUR", "payerId": %d}
                        """.formatted(payerId))
                .when().post("/api/v1/quotations")
                .then().statusCode(200)
                .extract().path("quotationId");

        // --- 3. Initiation du transfert.
        final String txIdRaw = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"quotationId": "%s", "beneficiaryId": "%s"}
                        """.formatted(quotationId, beneficiaryId))
                .when().post("/api/v1/transfers")
                .then().statusCode(201)
                .extract().path("id");
        final UUID txId = UUID.fromString(txIdRaw);
        assertEquals(TxStatus.INITIATED, status(txId));

        // --- 4. Collecte Swan : webhook BOOKED → AML_CLEARED → confirmation Thunes.
        Mockito.when(thunesGateway.confirmTransfer(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(new ThunesTransferResult(9001L, 9002L, "CONFIRMED"));

        final String swanBody = """
                {"eventType":"TRANSACTION_BOOKED","accountId":"swan-acc-1","iban":"%s",
                 "transactionId":"swan-tx-1","amount":100.00,"currency":"EUR"}
                """.formatted(IBAN);
        given().contentType(ContentType.JSON).body(swanBody)
                .header("X-Swan-Signature", WebhookSignatures.sign(swanBody, WebhookSignatures.SWAN_SECRET))
                .when().post("/webhooks/swan")
                .then().statusCode(200);

        swanWebhookConsumer.onMessage(latestOutboxPayload("swan-webhooks", txId));
        assertEquals(TxStatus.CONFIRMED, status(txId));
        Mockito.verify(thunesGateway).confirmTransfer(Mockito.any(), Mockito.any(), Mockito.any());

        // --- 5. Callback Thunes 40000 → PROCESSING.
        postThunesCallback(txId, "40000");
        assertEquals(TxStatus.PROCESSING, status(txId));

        // --- 6. Callback Thunes 70000 → DELIVERED.
        postThunesCallback(txId, "70000");
        assertEquals(TxStatus.DELIVERED, status(txId));
        assertNotNull(freshTx(txId).getCompletedAt());

        // --- 7. Événement transfer-events DELIVERED → monthly_used mis à jour.
        transferEventConsumer.onMessage(latestTransferEventPayload(txId, "DELIVERED"));
        final KycProfile kyc = QuarkusTransaction.requiringNew()
                .call(() -> kycProfiles.findByUserId(userId).orElseThrow());
        assertEquals(0, new BigDecimal("100.00").compareTo(kyc.getMonthlyUsed()),
                "monthly_used doit refléter le montant livré");
    }

    // --- Helpers ----------------------------------------------------------

    /** Poste un callback Thunes signé puis rejoue le message via le consumer. */
    private void postThunesCallback(UUID txId, String thunesStatus) {
        final Transaction tx = freshTx(txId);
        final String body = """
                {"id": 9002, "external_id": "%s", "status": "%s", "status_message": "test"}
                """.formatted(tx.getThunesExternalId(), thunesStatus);
        given().contentType(ContentType.JSON).body(body)
                .header("X-Thunes-Signature", WebhookSignatures.sign(body, WebhookSignatures.THUNES_SECRET))
                .when().post("/webhooks/thunes")
                .then().statusCode(200);

        thunesCallbackConsumer.onMessage(latestOutboxPayload("thunes-callbacks", txId));
    }

    /** Dernier payload enfilé dans kafka_outbox pour ce topic et cet agrégat. */
    private String latestOutboxPayload(String topic, UUID aggregateId) {
        final List<KafkaOutbox> rows = outbox.list("kafkaTopic = ?1 and aggregateId = ?2 order by createdAt",
                topic, aggregateId);
        assertTrue(!rows.isEmpty(), "une entrée outbox doit exister pour " + topic);
        return rows.get(rows.size() - 1).getPayload();
    }

    private String latestTransferEventPayload(UUID txId, String expectedStatus) {
        final List<KafkaOutbox> rows = outbox.list("kafkaTopic = ?1 and aggregateId = ?2 order by createdAt",
                "transfer-events", txId);
        return rows.stream()
                .map(KafkaOutbox::getPayload)
                .filter(p -> p.contains("\"" + expectedStatus + "\""))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("aucun événement transfer-events " + expectedStatus));
    }

    private TxStatus status(UUID txId) {
        return freshTx(txId).getStatus();
    }

    /**
     * Relit la transaction dans une transaction dédiée : le cache de premier
     * niveau de la session ambiante (request-scoped) conserverait sinon l'état
     * chargé avant les mutations commises par les consumers.
     */
    private Transaction freshTx(UUID txId) {
        return QuarkusTransaction.requiringNew()
                .call(() -> transactions.findByIdOptional(txId).orElseThrow());
    }
}
