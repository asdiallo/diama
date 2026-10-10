package com.zalamtech.diama.flow;

import com.zalamtech.diama.common.dto.SwanAccount;
import com.zalamtech.diama.domain.audit.KafkaOutbox;
import com.zalamtech.diama.domain.audit.KafkaOutboxRepository;
import com.zalamtech.diama.domain.kyc.KycProfile;
import com.zalamtech.diama.domain.kyc.KycProfileRepository;
import com.zalamtech.diama.domain.kyc.KycVerificationStatus;
import com.zalamtech.diama.domain.kyc.DiditGateway;
import com.zalamtech.diama.domain.kyc.DiditSession;
import com.zalamtech.diama.domain.transaction.SwanGateway;
import com.zalamtech.diama.domain.user.IdentityGateway;
import com.zalamtech.diama.domain.user.User;
import com.zalamtech.diama.domain.user.UserRepository;
import com.zalamtech.diama.infra.kafka.KycEventConsumer;
import com.zalamtech.diama.infra.kafka.DiditWebhookConsumer;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Flux KYC complet : init Didit (session) → webhook {@code status.updated}
 * Approved → montée au niveau 2 → propagation Keycloak ({@code kyc_level}) et
 * provisionnement du compte Swan dédié, via le trigger SQL
 * {@code sync_kyc_level_to_keycloak} et le consumer {@code kyc-events}.
 */
@QuarkusTest
class KycFlowIT {

    private static final String SUB = "88888888-8888-4888-8888-888888888888";
    private static final String SESSION_ID = "didit-session-kycflow";

    @Inject
    UserRepository users;
    @Inject
    KycProfileRepository kycProfiles;
    @Inject
    KafkaOutboxRepository outbox;
    @Inject
    DiditWebhookConsumer diditWebhookConsumer;
    @Inject
    KycEventConsumer kycEventConsumer;

    @InjectMock
    DiditGateway diditGateway;
    @InjectMock
    IdentityGateway identityGateway;
    @InjectMock
    SwanGateway swanGateway;

    @Test
    @TestSecurity(user = SUB, roles = "user")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = SUB),
            @Claim(key = "email", value = "flow-kyc@example.com"),
            @Claim(key = "phone_number", value = "+33612348888"),
            @Claim(key = "given_name", value = "Ibrahima"),
            @Claim(key = "family_name", value = "Sow")
    })
    void kyc_upgrade_flow_propagates_to_keycloak_and_provisions_swan() {
        // --- 0. Utilisateur niveau 1 déjà vérifié (montée visée : niveau 2).
        given().when().get("/api/mobile/me").then().statusCode(200);
        final UUID userId = users.findByKeycloakId(SUB).orElseThrow().getId();
        QuarkusTransaction.requiringNew().run(() -> {
            // Ré-exécutions sur conteneur DevServices réutilisé : upsert du profil
            // et remise à l'état initial (pas de session Didit, pas de compte Swan).
            final User user = users.findByIdOptional(userId).orElseThrow();
            user.setSwanAccountId(null);
            user.setSwanIban(null);
            final KycProfile kyc = kycProfiles.findByUserId(userId).orElseGet(() -> {
                final KycProfile created = KycProfile.builder().userId(userId).build();
                kycProfiles.persist(created);
                return created;
            });
            kyc.setKycLevel((short) 1);
            kyc.setVerificationStatus(KycVerificationStatus.APPROVED);
            kyc.setDiditSessionId(null);
            kyc.setMonthlyLimit(new BigDecimal("300"));
            kyc.setMonthlyUsed(BigDecimal.ZERO);
            kyc.setLimitResetDate(LocalDate.now().plusMonths(1));
        });

        // --- 1. Init Didit : session créée sur le workflow du niveau 2.
        Mockito.when(diditGateway.createSession("wf-level-2", userId.toString()))
                .thenReturn(new DiditSession(SESSION_ID, "session-token-123",
                        "https://verify.didit.me/en/session/session-token-123"));

        given().when().post("/api/v1/kyc/init").then()
                .statusCode(200)
                .body("sessionId", is(SESSION_ID))
                .body("sessionToken", is("session-token-123"))
                .body("verificationUrl", notNullValue())
                .body("targetLevel", is(2));

        // --- 2. Webhook Didit Approved, signé HMAC + horodaté → enfilé dans l'outbox.
        final String webhookBody = """
                {"session_id":"%s","status":"Approved","webhook_type":"status.updated",
                 "workflow_id":"wf-level-2","vendor_data":"%s","timestamp":%d,
                 "decision":{"session_id":"%s","status":"Approved"}}
                """.formatted(SESSION_ID, userId, Instant.now().getEpochSecond(), SESSION_ID);
        given().contentType(ContentType.JSON).body(webhookBody)
                .header("X-Signature", WebhookSignatures.sign(webhookBody, WebhookSignatures.DIDIT_SECRET))
                .header("X-Timestamp", String.valueOf(Instant.now().getEpochSecond()))
                .when().post("/webhooks/didit")
                .then().statusCode(200);

        // --- 3. Consumer didit-webhooks → upgrade niveau 2 (limite 3000).
        diditWebhookConsumer.onMessage(latestPayload("didit-webhooks"));

        // Relectures en transaction dédiée : évite le cache de premier niveau
        // de la session ambiante (état antérieur aux mutations des consumers).
        final KycProfile upgraded = QuarkusTransaction.requiringNew()
                .call(() -> kycProfiles.findByUserId(userId).orElseThrow());
        assertEquals(2, (int) upgraded.getKycLevel());
        assertEquals(KycVerificationStatus.APPROVED, upgraded.getVerificationStatus());
        assertEquals(0, new BigDecimal("3000").compareTo(upgraded.getMonthlyLimit()));

        // --- 4. Le trigger SQL sync_kyc_level_to_keycloak a enfilé kyc-events.
        final String kycEvent = latestForAggregate("kyc-events", userId);
        assertTrue(kycEvent.contains("\"kyc_level\""), "payload jsonb du trigger attendu");

        // --- 5. Consumer kyc-events → Keycloak + provisionnement Swan (niveau 2).
        Mockito.when(swanGateway.createAccount(Mockito.any()))
                .thenReturn(new SwanAccount("swan-acc-kyc", "FR7699999000011111111111188", "SWNBFR22",
                        "Ibrahima Sow", null, "Enabled"));

        kycEventConsumer.onMessage(kycEvent);

        Mockito.verify(identityGateway).updateKycLevel(SUB, 2);
        final User user = QuarkusTransaction.requiringNew()
                .call(() -> users.findByIdOptional(userId).orElseThrow());
        assertEquals("swan-acc-kyc", user.getSwanAccountId());
        assertEquals("FR7699999000011111111111188", user.getSwanIban());
    }

    // --- Helpers ----------------------------------------------------------

    private String latestPayload(String topic) {
        final List<KafkaOutbox> rows = outbox.list("kafkaTopic = ?1 order by createdAt", topic);
        assertTrue(!rows.isEmpty(), "une entrée outbox doit exister pour " + topic);
        return rows.get(rows.size() - 1).getPayload();
    }

    private String latestForAggregate(String topic, UUID aggregateId) {
        final List<KafkaOutbox> rows = outbox.list("kafkaTopic = ?1 and aggregateId = ?2 order by createdAt",
                topic, aggregateId);
        assertTrue(!rows.isEmpty(), "une entrée outbox doit exister pour " + topic);
        return rows.get(rows.size() - 1).getPayload();
    }
}
