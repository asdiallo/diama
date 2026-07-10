package com.zalamtech.diama.api.resource;

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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Couvre {@link UserResource} de bout en bout (@QuarkusTest + DevServices
 * Postgres). L'identité est fournie par {@code @TestSecurity}/{@code @JwtSecurity}
 * (OIDC désactivé en profil test).
 *
 * <p>Le conteneur DevServices pouvant être réutilisé entre exécutions, les
 * assertions n'exigent jamais un état vide : identifiants uniques par test,
 * upsert du profil KYC, et vérifications par présence/absence plutôt que par
 * cardinalité exacte.</p>
 */
@QuarkusTest
class UserResourceTest {

    private static final String USERS = "/api/v1/users";

    /** Sujet principal : utilisateur avec profil KYC (tests profil + limites). */
    private static final String USER_SUB = "22222222-2222-2222-2222-222222222222";
    /** Sujet secondaire sans profil KYC (test du 404 sur /me/limits). */
    private static final String NO_KYC_SUB = "33333333-3333-3333-3333-333333333333";

    @Inject
    UserRepository users;

    @Inject
    KycProfileRepository kycProfiles;

    // ---------------------------------------------------------------------
    //  Authentification
    // ---------------------------------------------------------------------

    @Test
    void anonymous_is_unauthorized() {
        given().when().get(USERS + "/me").then().statusCode(401);
    }

    // ---------------------------------------------------------------------
    //  GET /me — profil public (sans keycloak_id)
    // ---------------------------------------------------------------------

    @Test
    @TestSecurity(user = USER_SUB, roles = "user")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_SUB),
            @Claim(key = "kyc_level", value = "2"),
            @Claim(key = "sid", value = "sess-user-2"),
            @Claim(key = "azp", value = "mobile-app"),
            @Claim(key = "email", value = "fatou@example.com"),
            @Claim(key = "phone_number", value = "+33600000222"),
            @Claim(key = "given_name", value = "Fatou"),
            @Claim(key = "family_name", value = "Ba")
    })
    void me_returns_public_profile_without_keycloak_id() {
        given().when().get(USERS + "/me").then()
                .statusCode(200)
                .body("id", notNullValue())
                .body("email", is("fatou@example.com"))   // synchronisé depuis le JWT à chaque requête
                .body("status", notNullValue())
                .body("keycloakId", is(nullValue()));      // jamais exposé
    }

    // ---------------------------------------------------------------------
    //  PUT /me — mise à jour + validation
    // ---------------------------------------------------------------------

    @Test
    @TestSecurity(user = USER_SUB, roles = "user")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_SUB),
            @Claim(key = "sid", value = "sess-user-2"),
            @Claim(key = "azp", value = "mobile-app"),
            @Claim(key = "email", value = "fatou@example.com"),
            @Claim(key = "phone_number", value = "+33600000222"),
            @Claim(key = "given_name", value = "Fatou"),
            @Claim(key = "family_name", value = "Ba")
    })
    void update_profile_applies_partial_changes() {
        given().contentType(ContentType.JSON)
                .body("{\"firstName\":\"Mariam\",\"preferredCurrency\":\"USD\"}")
                .when().put(USERS + "/me").then()
                .statusCode(200)
                .body("firstName", is("Mariam"))
                .body("preferredCurrency", is("USD"));
    }

    @Test
    @TestSecurity(user = USER_SUB, roles = "user")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_SUB),
            @Claim(key = "azp", value = "mobile-app"),
            @Claim(key = "email", value = "fatou@example.com"),
            @Claim(key = "phone_number", value = "+33600000222")
    })
    void update_profile_rejects_invalid_phone() {
        given().contentType(ContentType.JSON)
                .body("{\"phone\":\"12345\"}")
                .when().put(USERS + "/me").then()
                .statusCode(400)
                .body("code", is("VALIDATION_ERROR"))
                .body("violations.field", hasItem("phone"));
    }

    // ---------------------------------------------------------------------
    //  Bénéficiaires — cycle de vie complet
    // ---------------------------------------------------------------------

    @Test
    @TestSecurity(user = USER_SUB, roles = "user")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_SUB),
            @Claim(key = "azp", value = "mobile-app"),
            @Claim(key = "email", value = "fatou@example.com"),
            @Claim(key = "phone_number", value = "+33600000222")
    })
    void beneficiary_add_list_then_deactivate() {
        final String nickname = "ben-" + UUID.randomUUID().toString().substring(0, 8);

        // POST → 201 + corps
        final String id = given().contentType(ContentType.JSON)
                .body("""
                        {
                          "nickname": "%s",
                          "firstName": "Amadou",
                          "lastName": "Sow",
                          "phoneMsisdn": "+221771234567",
                          "countryIso": "SN",
                          "currency": "XOF",
                          "payerService": "MobileWallet"
                        }
                        """.formatted(nickname))
                .when().post(USERS + "/me/beneficiaries").then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("nickname", is(nickname))
                .body("active", is(true))
                .extract().path("id");

        // GET → le bénéficiaire actif apparaît
        given().when().get(USERS + "/me/beneficiaries").then()
                .statusCode(200)
                .body("nickname", hasItem(nickname));

        // DELETE → 204 (soft delete)
        given().when().delete(USERS + "/me/beneficiaries/" + id).then().statusCode(204);

        // GET → disparaît de la liste active
        given().when().get(USERS + "/me/beneficiaries").then()
                .statusCode(200)
                .body("nickname", not(hasItem(nickname)));
    }

    @Test
    @TestSecurity(user = USER_SUB, roles = "user")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_SUB),
            @Claim(key = "azp", value = "mobile-app"),
            @Claim(key = "email", value = "fatou@example.com"),
            @Claim(key = "phone_number", value = "+33600000222")
    })
    void beneficiary_creation_is_validated() {
        given().contentType(ContentType.JSON)
                .body("{\"lastName\":\"Sow\",\"countryIso\":\"SENEGAL\",\"currency\":\"XOF\"}")
                .when().post(USERS + "/me/beneficiaries").then()
                .statusCode(400)
                .body("code", is("VALIDATION_ERROR"))
                .body("violations.field", hasItem("firstName"))
                .body("violations.field", hasItem("countryIso"));
    }

    @Test
    @TestSecurity(user = USER_SUB, roles = "user")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_SUB),
            @Claim(key = "azp", value = "mobile-app"),
            @Claim(key = "email", value = "fatou@example.com"),
            @Claim(key = "phone_number", value = "+33600000222")
    })
    void deactivating_unknown_beneficiary_returns_404() {
        given().when().delete(USERS + "/me/beneficiaries/" + UUID.randomUUID()).then()
                .statusCode(404)
                .body("code", is("RESOURCE_NOT_FOUND"));
    }

    // ---------------------------------------------------------------------
    //  POST /me/devices
    // ---------------------------------------------------------------------

    @Test
    @TestSecurity(user = USER_SUB, roles = "user")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_SUB),
            @Claim(key = "azp", value = "mobile-app"),
            @Claim(key = "email", value = "fatou@example.com"),
            @Claim(key = "phone_number", value = "+33600000222")
    })
    void register_device_returns_201() {
        final String token = "tok-" + UUID.randomUUID();
        given().contentType(ContentType.JSON)
                .body("{\"deviceToken\":\"%s\",\"platform\":\"ANDROID\",\"appVersion\":\"1.2.3\"}".formatted(token))
                .when().post(USERS + "/me/devices").then()
                .statusCode(201);
    }

    @Test
    @TestSecurity(user = USER_SUB, roles = "user")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_SUB),
            @Claim(key = "azp", value = "mobile-app"),
            @Claim(key = "email", value = "fatou@example.com"),
            @Claim(key = "phone_number", value = "+33600000222")
    })
    void register_device_rejects_invalid_platform() {
        given().contentType(ContentType.JSON)
                .body("{\"deviceToken\":\"tok-x\",\"platform\":\"WINDOWS\"}")
                .when().post(USERS + "/me/devices").then()
                .statusCode(400)
                .body("code", is("VALIDATION_ERROR"))
                .body("violations.field", hasItem("platform"));
    }

    // ---------------------------------------------------------------------
    //  GET /me/limits
    // ---------------------------------------------------------------------

    @Test
    @TestSecurity(user = USER_SUB, roles = "user")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = USER_SUB),
            @Claim(key = "kyc_level", value = "2"),
            @Claim(key = "azp", value = "mobile-app"),
            @Claim(key = "email", value = "fatou@example.com"),
            @Claim(key = "phone_number", value = "+33600000222")
    })
    void limits_reflect_kyc_profile() {
        // Crée l'utilisateur (première requête) puis upsert son profil KYC.
        given().when().get(USERS + "/me").then().statusCode(200);
        upsertKycProfile(USER_SUB, (short) 2, "3000.00", "500.00");

        given().when().get(USERS + "/me/limits").then()
                .statusCode(200)
                .body("kycLevel", is(2))
                .body("allowed", is(true))              // montant testé = 0
                .body("limit", is(3000.0f))
                .body("used", is(500.0f))
                .body("available", is(2500.0f));
    }

    @Test
    @TestSecurity(user = NO_KYC_SUB, roles = "user")
    @JwtSecurity(claims = {
            @Claim(key = "sub", value = NO_KYC_SUB),
            @Claim(key = "azp", value = "mobile-app"),
            @Claim(key = "email", value = "no-kyc@example.com"),
            @Claim(key = "phone_number", value = "+33600000333")
    })
    void limits_without_kyc_profile_returns_404() {
        given().when().get(USERS + "/me/limits").then()
                .statusCode(404)
                .body("code", is("RESOURCE_NOT_FOUND"));
    }

    // ---------------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------------

    /** Crée ou met à jour le profil KYC de l'utilisateur (idempotent sur conteneur réutilisé). */
    private void upsertKycProfile(String keycloakId, short level, String limit, String used) {
        QuarkusTransaction.requiringNew().run(() -> {
            final User user = users.findByKeycloakId(keycloakId).orElseThrow();
            final LocalDate nextReset = LocalDate.now().with(TemporalAdjusters.firstDayOfNextMonth());
            final KycProfile existing = kycProfiles.findByUserId(user.getId()).orElse(null);
            if (existing != null) {
                existing.setKycLevel(level);
                existing.setMonthlyLimit(new BigDecimal(limit));
                existing.setMonthlyUsed(new BigDecimal(used));
                existing.setLimitResetDate(nextReset);
            } else {
                kycProfiles.persist(KycProfile.builder()
                        .userId(user.getId())
                        .kycLevel(level)
                        .monthlyLimit(new BigDecimal(limit))
                        .monthlyUsed(new BigDecimal(used))
                        .limitResetDate(nextReset)
                        .build());
            }
        });
    }
}
