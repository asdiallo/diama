package com.zalamtech.diama.api.security;

import com.zalamtech.diama.domain.user.SessionClient;
import com.zalamtech.diama.domain.user.User;
import com.zalamtech.diama.domain.user.UserRepository;
import com.zalamtech.diama.domain.user.UserSession;
import com.zalamtech.diama.domain.user.UserSessionRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Couvre la couche de sécurité :
 * <ul>
 *   <li>authentification requise + extraction des claims (endpoints) ;</li>
 *   <li>enforcement {@code @RolesAllowed} par rôle ;</li>
 *   <li>mapping de persistance runtime (enum natif session_client, inet, id v7).</li>
 * </ul>
 */
@QuarkusTest
class SecurityLayerTest {

    private static final String USER_SUB = "11111111-1111-1111-1111-111111111111";

    @Inject
    UserRepository users;

    @Inject
    UserSessionRepository sessions;

    // --- Authentification requise ---------------------------------------

    @Test
    void anonymous_is_unauthorized() {
        given().when().get("/api/mobile/me").then().statusCode(401);
    }

    // --- Extraction des claims via l'endpoint mobile --------------------

    @Test
    @TestSecurity(user = USER_SUB, roles = "user")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_SUB),
            @Claim(key = "kyc_level", value = "2"),
            @Claim(key = "sid", value = "sess-mobile-1"),
            @Claim(key = "azp", value = "mobile-app"),
            @Claim(key = "email", value = "awa@example.com"),
            @Claim(key = "phone_number", value = "+33600000042"),
            @Claim(key = "given_name", value = "Awa"),
            @Claim(key = "family_name", value = "Diallo")
    })
    void user_reads_own_synchronized_profile() {
        given().when().get("/api/mobile/me").then()
                .statusCode(200)
                .body("userId", notNullValue())               // PK interne (UUID v7)
                .body("keycloakId", is(USER_SUB))
                .body("email", is("awa@example.com"))
                .body("firstName", is("Awa"))
                .body("status", is("PENDING_VERIFICATION"))    // défaut à la création
                .body("kycLevel", is(2))
                .body("sessionId", is("sess-mobile-1"))
                .body("client", is("mobile-app"));
    }

    // --- @RolesAllowed : refus / autorisation ---------------------------

    @Test
    @TestSecurity(user = USER_SUB, roles = "user")
    void user_cannot_access_admin_config() {
        given().when().get("/api/admin/config").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin-1", roles = "admin")
    void admin_can_access_admin_config() {
        given().when().get("/api/admin/config").then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "kyc-1", roles = "kyc-agent")
    void kyc_agent_can_access_kyc_but_not_transactions() {
        given().when().get("/api/v1/admin/kyc/pending").then().statusCode(200);
        given().when().get("/api/v1/admin/transactions").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "ops-1", roles = "ops-agent")
    void ops_agent_can_access_transactions_but_not_kyc() {
        given().when().get("/api/v1/admin/transactions").then().statusCode(200);
        given().when().get("/api/v1/admin/kyc/pending").then().statusCode(403);
    }

    // --- Mapping de persistance runtime (Hibernate ↔ PostgreSQL) --------
    // Valide en conditions réelles : enum natif session_client (cast ?::),
    // colonne inet, et génération d'ID UUID v7 côté application.

    @Test
    void persists_user_and_session_with_native_enum_inet_and_v7_id() {
        // Identifiants uniques : le conteneur DevServices peut être réutilisé
        // entre exécutions (contraintes UNIQUE keycloak_id / phone / sid).
        final String uniq = UUID.randomUUID().toString().substring(0, 8);
        final String sid = "sess-" + uniq;

        final UUID sessionId = QuarkusTransaction.requiringNew().call(() -> {
            User u = User.builder()
                    .keycloakId("kc-" + uniq)
                    .email(uniq + "@example.com")
                    .phone("+3360" + Long.toString(System.nanoTime() % 100000000L))
                    .firstName("Djeneba")
                    .lastName("Barry")
                    .countryOfResidence("FR")
                    .build();
            users.persist(u);

            UserSession s = UserSession.builder()
                    .userId(u.getId())
                    .keycloakSessionId(sid)
                    .client(SessionClient.MOBILE_APP)
                    .ipAddress("127.0.0.1")
                    .isActive(Boolean.TRUE)
                    .lastSeenAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .build();
            sessions.persist(s);
            return s.getId();
        });

        // ID v7 (time-ordered) généré côté application
        assertEquals(7, sessionId.version(), "l'ID de session doit être un UUID v7");

        // Relecture : enum natif et inet correctement (dé)sérialisés
        UserSession reloaded = QuarkusTransaction.requiringNew()
                .call(() -> sessions.findByKeycloakSessionId(sid).orElseThrow());
        assertEquals(SessionClient.MOBILE_APP, reloaded.getClient());
        assertEquals("127.0.0.1", reloaded.getIpAddress());
        assertEquals(7, reloaded.getUserId().version(), "l'ID utilisateur doit être un UUID v7");
    }
}
