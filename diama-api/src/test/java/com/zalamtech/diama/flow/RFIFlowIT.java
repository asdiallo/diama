package com.zalamtech.diama.flow;

import com.zalamtech.diama.domain.audit.KafkaOutbox;
import com.zalamtech.diama.domain.audit.KafkaOutboxRepository;
import com.zalamtech.diama.domain.transaction.RfiInfo;
import com.zalamtech.diama.domain.transaction.RfiRequest;
import com.zalamtech.diama.domain.transaction.RfiRequestRepository;
import com.zalamtech.diama.domain.transaction.RfiStatus;
import com.zalamtech.diama.domain.transaction.ThunesTransferGateway;
import com.zalamtech.diama.domain.transaction.Transaction;
import com.zalamtech.diama.domain.transaction.TransactionRepository;
import com.zalamtech.diama.domain.transaction.TxStatus;
import com.zalamtech.diama.domain.user.AdminRole;
import com.zalamtech.diama.domain.user.AdminUser;
import com.zalamtech.diama.domain.user.AdminUserRepository;
import com.zalamtech.diama.domain.user.User;
import com.zalamtech.diama.domain.user.UserBeneficiary;
import com.zalamtech.diama.domain.user.UserBeneficiaryRepository;
import com.zalamtech.diama.domain.user.UserRepository;
import com.zalamtech.diama.infra.kafka.ThunesCallbackConsumer;
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
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flux RFI (compliance Thunes) : callback {@code 20000} → transaction
 * {@code UNDER_REVIEW} + RFI persistée → soumission des documents par le
 * backoffice → callback {@code 40000} → {@code PROCESSING}.
 */
@QuarkusTest
class RFIFlowIT {

    private static final String ADMIN_SUB = "ops-rfi-agent";

    // Identifiants Thunes uniques par exécution : le conteneur DevServices peut
    // être réutilisé, une valeur constante apparierait la RFI d'un run précédent.
    private final long thunesTxId = java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000, 900_000_000);
    private final long thunesRfiId = java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000, 2_000_000_000);

    @Inject
    UserRepository users;
    @Inject
    UserBeneficiaryRepository beneficiaries;
    @Inject
    AdminUserRepository adminUsers;
    @Inject
    TransactionRepository transactions;
    @Inject
    RfiRequestRepository rfiRequests;
    @Inject
    KafkaOutboxRepository outbox;
    @Inject
    ThunesCallbackConsumer thunesCallbackConsumer;

    @InjectMock
    ThunesTransferGateway thunesGateway;

    private UUID txId;
    private String externalId;

    @BeforeEach
    void seedConfirmedTransaction() {
        final String uniq = UUID.randomUUID().toString().substring(0, 8);
        externalId = UUID.randomUUID().toString();

        QuarkusTransaction.requiringNew().run(() -> {
            if (adminUsers.findByKeycloakId(ADMIN_SUB).isEmpty()) {
                adminUsers.persist(AdminUser.builder()
                        .keycloakId(ADMIN_SUB).email("ops-rfi@diama.app").fullName("Agent Ops RFI")
                        .role(AdminRole.OPS_AGENT).isActive(Boolean.TRUE)
                        .build());
            }

            final User user = User.builder()
                    .keycloakId("kc-rfi-" + uniq)
                    .email("rfi-" + uniq + "@example.com")
                    .phone("+3362" + Long.toString(System.nanoTime() % 100000000L))
                    .firstName("Kadiatou").lastName("Baldé").countryOfResidence("FR")
                    .build();
            users.persist(user);

            final UserBeneficiary beneficiary = UserBeneficiary.builder()
                    .userId(user.getId()).firstName("Alpha").lastName("Condé")
                    .phoneMsisdn("+224622222222").countryIso("GN").currency("GNF")
                    .payerService("MobileWallet").isActive(Boolean.TRUE)
                    .build();
            beneficiaries.persist(beneficiary);

            final Transaction tx = Transaction.builder()
                    .userId(user.getId()).beneficiaryId(beneficiary.getId())
                    .status(TxStatus.CONFIRMED)
                    .sourceCurrency("EUR").sourceAmount(new BigDecimal("250.00"))
                    .destCurrency("GNF").destAmount(new BigDecimal("2940000.00"))
                    .thunesExternalId(externalId).thunesTransactionId(thunesTxId)
                    .build();
            transactions.persist(tx);
            txId = tx.getId();
        });
    }

    @Test
    @TestSecurity(user = ADMIN_SUB, roles = "ops-agent")
    @JwtSecurity(claims = @Claim(key = "sub", value = ADMIN_SUB))
    void rfi_flow_from_under_review_to_processing() {
        // --- 1. Callback Thunes 20000 → UNDER_REVIEW, RFI récupérée et persistée.
        Mockito.when(thunesGateway.fetchOpenRFI(thunesTxId)).thenReturn(Optional.of(new RfiInfo(
                thunesRfiId,
                "{\"sender\":[\"date_of_birth\"]}",
                List.of("identification_documents"),
                "Justificatif d'identité requis",
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(5))));

        postThunesCallback("20000");
        assertEquals(TxStatus.UNDER_REVIEW, status());

        final RfiRequest rfi = rfiRequests.findByThunesRfiId(thunesRfiId).orElseThrow();
        assertEquals(RfiStatus.PENDING, rfi.getStatus());
        assertEquals(txId, rfi.getTransactionId());

        // --- 2. La RFI apparaît dans la file backoffice.
        given().when().get("/api/v1/admin/transactions/rfi-pending").then()
                .statusCode(200)
                .body("thunesRfiId", hasItem((int) thunesRfiId));

        // --- 3. Soumission des documents par l'agent ops.
        final String docBase64 = Base64.getEncoder()
                .encodeToString("scan-passeport".getBytes(StandardCharsets.UTF_8));
        given().contentType(ContentType.JSON)
                .body("""
                        {"documents":[{"docType":"identification_documents","mimeType":"application/pdf",
                          "contentBase64":"%s"}],
                         "info":{"sender.date_of_birth":"1990-01-01"}}
                        """.formatted(docBase64))
                .when().post("/api/v1/admin/transactions/%s/rfi/%s/submit".formatted(txId, rfi.getId()))
                .then().statusCode(204);

        Mockito.verify(thunesGateway).uploadRFIDocument(Mockito.eq(thunesRfiId), Mockito.any(), Mockito.eq("application/pdf"));
        Mockito.verify(thunesGateway).submitRFIInfo(thunesRfiId, java.util.Map.of("sender.date_of_birth", "1990-01-01"));
        assertEquals(RfiStatus.SUBMITTED, freshRfiStatus());

        // --- 4. Compliance levée : callback 40000 → PROCESSING.
        postThunesCallback("40000");
        assertEquals(TxStatus.PROCESSING, status());
    }

    // --- Helpers ----------------------------------------------------------

    private void postThunesCallback(String thunesStatus) {
        final String body = """
                {"id": %d, "external_id": "%s", "status": "%s", "status_message": "test"}
                """.formatted(thunesTxId, externalId, thunesStatus);
        given().contentType(ContentType.JSON).body(body)
                .header("X-Thunes-Signature", WebhookSignatures.sign(body, WebhookSignatures.THUNES_SECRET))
                .when().post("/webhooks/thunes")
                .then().statusCode(200);

        final List<KafkaOutbox> rows = outbox.list("kafkaTopic = ?1 and aggregateId = ?2 order by createdAt",
                "thunes-callbacks", txId);
        assertTrue(!rows.isEmpty(), "le webhook doit avoir enfilé une entrée outbox");
        thunesCallbackConsumer.onMessage(rows.get(rows.size() - 1).getPayload());
    }

    /**
     * Relectures dans une transaction dédiée : le cache de premier niveau de la
     * session ambiante conserverait sinon l'état chargé avant les mutations
     * commises par les consumers / les requêtes REST.
     */
    private TxStatus status() {
        return QuarkusTransaction.requiringNew()
                .call(() -> transactions.findByIdOptional(txId).orElseThrow().getStatus());
    }

    private RfiStatus freshRfiStatus() {
        return QuarkusTransaction.requiringNew()
                .call(() -> rfiRequests.findByThunesRfiId(thunesRfiId).orElseThrow().getStatus());
    }
}
