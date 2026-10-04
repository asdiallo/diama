package com.zalamtech.diama.api.resource;

import com.zalamtech.diama.common.exception.RegistrationConflictException;
import com.zalamtech.diama.common.exception.WeakPasswordException;
import com.zalamtech.diama.domain.support.RateLimiter;
import com.zalamtech.diama.domain.user.EmailVerificationState;
import com.zalamtech.diama.domain.user.IdentityGateway;
import com.zalamtech.diama.domain.user.NewIdentity;
import com.zalamtech.diama.domain.user.User;
import com.zalamtech.diama.domain.user.UserRegistrationService;
import com.zalamtech.diama.domain.user.UserRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Couvre {@link AuthResource} (@QuarkusTest + DevServices Postgres). Keycloak
 * est remplacé au niveau du port {@link IdentityGateway}.
 *
 * <p>Comme dans {@code UserResourceTest}, le conteneur peut être réutilisé :
 * chaque test génère un email et un téléphone uniques.</p>
 */
@QuarkusTest
class AuthResourceTest {

    private static final String REGISTER = "/api/v1/auth/register";

    @InjectMock
    IdentityGateway identity;

    @Inject
    UserRepository users;

    @Inject
    UserRegistrationService registration;

    @Inject
    RateLimiter rateLimiter;

    private static final String RESEND = "/api/v1/auth/resend-verification";

    /** Inscrit un compte et retourne l'identifiant Keycloak que le mock lui a attribué. */
    private String registerAccount(String email) {
        final String keycloakId = UUID.randomUUID().toString();
        when(identity.createUser(any())).thenReturn(keycloakId);
        given().contentType(ContentType.JSON).body(payload(email, uniquePhone()))
                .when().post(REGISTER).then().statusCode(201);
        return keycloakId;
    }

    /** `created_at` n'est pas modifiable par l'entité : on vieillit la ligne en SQL. */
    private void backdate(String keycloakId, int days) {
        QuarkusTransaction.requiringNew().run(() -> users.getEntityManager()
                .createNativeQuery("update users set created_at = now() - make_interval(days => :days) "
                        + "where keycloak_id = :kid")
                .setParameter("days", days)
                .setParameter("kid", keycloakId)
                .executeUpdate());
    }

    private boolean pending(String keycloakId) {
        return QuarkusTransaction.requiringNew().call(() -> users.findByKeycloakId(keycloakId)
                .map(user -> Boolean.TRUE.equals(user.getEmailVerificationPending()))
                .orElseThrow());
    }

    private boolean exists(String keycloakId) {
        return QuarkusTransaction.requiringNew().call(() -> users.findByKeycloakId(keycloakId).isPresent());
    }

    private static String uniqueEmail() {
        return "reg-" + UUID.randomUUID() + "@example.com";
    }

    /** E.164 unique : +336 suivi de 8 chiffres tirés d'un UUID. */
    private static String uniquePhone() {
        final long n = Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000_000L);
        return "+336" + String.format("%08d", n);
    }

    private static Map<String, String> payload(String email, String phone) {
        return Map.of(
                "email", email,
                "phone", phone,
                "firstName", "Aïssatou",
                "lastName", "Diallo",
                "countryOfResidence", "FR",
                "password", "Sup3r-secret!");
    }

    @Test
    void register_is_public_and_creates_keycloak_account_and_user_row() {
        final String email = uniqueEmail();
        final String phone = uniquePhone();
        final String keycloakId = UUID.randomUUID().toString();
        when(identity.createUser(any())).thenReturn(keycloakId);

        // Email saisi en majuscules : il doit être normalisé avant Keycloak et la base.
        given().contentType(ContentType.JSON).body(payload(email.toUpperCase(), phone))
                .when().post(REGISTER).then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("email", is(email))
                .body("password", is((Object) null));

        final ArgumentCaptor<NewIdentity> sent = ArgumentCaptor.forClass(NewIdentity.class);
        verify(identity).createUser(sent.capture());
        assertEquals(email, sent.getValue().email());
        assertEquals("Sup3r-secret!", sent.getValue().password());

        final User row = QuarkusTransaction.requiringNew()
                .call(() -> users.findByKeycloakId(keycloakId).orElseThrow());
        assertEquals(email, row.getEmail());
        assertEquals(phone, row.getPhone());
        assertEquals("FR", row.getCountryOfResidence());

        // Compte marqué en attente, et email de confirmation envoyé.
        assertTrue(row.getEmailVerificationPending());
        verify(identity).sendVerificationEmail(keycloakId);
    }

    @Test
    void registration_succeeds_even_when_verification_email_cannot_be_sent() {
        doThrow(new IllegalStateException("SMTP down")).when(identity).sendVerificationEmail(anyString());

        final String keycloakId = registerAccount(uniqueEmail());

        assertTrue(exists(keycloakId));
        verify(identity, never()).deleteUser(anyString());
    }

    // ---------------------------------------------------------------------
    //  Renvoi de l'email de confirmation
    // ---------------------------------------------------------------------

    @Test
    void resend_sends_once_then_honours_the_cooldown() {
        final String email = uniqueEmail();
        final String keycloakId = registerAccount(email);
        when(identity.emailVerificationState(keycloakId)).thenReturn(EmailVerificationState.PENDING);

        given().contentType(ContentType.JSON).body(Map.of("email", email))
                .when().post(RESEND).then().statusCode(204);
        given().contentType(ContentType.JSON).body(Map.of("email", email))
                .when().post(RESEND).then().statusCode(204);

        // Un envoi à l'inscription + un seul renvoi : le second est dans le délai de garde.
        verify(identity, times(2)).sendVerificationEmail(keycloakId);
    }

    @Test
    void resend_for_unknown_email_is_a_silent_204() {
        given().contentType(ContentType.JSON).body(Map.of("email", uniqueEmail()))
                .when().post(RESEND).then().statusCode(204);

        verify(identity, never()).sendVerificationEmail(anyString());
        verify(identity, never()).emailVerificationState(anyString());
    }

    @Test
    void resend_for_already_verified_account_clears_the_flag_without_sending() {
        final String email = uniqueEmail();
        final String keycloakId = registerAccount(email);
        when(identity.emailVerificationState(keycloakId)).thenReturn(EmailVerificationState.VERIFIED);

        given().contentType(ContentType.JSON).body(Map.of("email", email))
                .when().post(RESEND).then().statusCode(204);

        verify(identity, times(1)).sendVerificationEmail(keycloakId); // celui de l'inscription
        assertFalse(pending(keycloakId));
    }

    // ---------------------------------------------------------------------
    //  Purge des inscriptions non confirmées
    // ---------------------------------------------------------------------

    @Test
    void purge_removes_old_unverified_accounts_from_keycloak_and_database() {
        final String stale = registerAccount(uniqueEmail());
        final String recent = registerAccount(uniqueEmail());
        backdate(stale, 30);
        // Le conteneur peut contenir d'anciennes lignes en attente : confirmées par défaut.
        when(identity.emailVerificationState(anyString())).thenReturn(EmailVerificationState.VERIFIED);
        when(identity.emailVerificationState(stale)).thenReturn(EmailVerificationState.PENDING);

        registration.purgeUnverified();

        verify(identity).deleteUser(stale);
        assertFalse(exists(stale));
        // Trop récent pour être purgé : ni interrogé, ni supprimé.
        verify(identity, never()).emailVerificationState(recent);
        assertTrue(exists(recent));
        assertTrue(pending(recent));
    }

    @Test
    void purge_keeps_accounts_confirmed_in_keycloak_and_stops_tracking_them() {
        final String confirmed = registerAccount(uniqueEmail());
        backdate(confirmed, 30);
        when(identity.emailVerificationState(anyString())).thenReturn(EmailVerificationState.VERIFIED);

        registration.purgeUnverified();

        verify(identity, never()).deleteUser(anyString());
        assertTrue(exists(confirmed));
        assertFalse(pending(confirmed));
    }

    @Test
    void purge_keeps_the_database_row_when_keycloak_deletion_fails() {
        final String stale = registerAccount(uniqueEmail());
        backdate(stale, 30);
        when(identity.emailVerificationState(anyString())).thenReturn(EmailVerificationState.VERIFIED);
        when(identity.emailVerificationState(stale)).thenReturn(EmailVerificationState.PENDING);
        doThrow(new IllegalStateException("Keycloak down")).when(identity).deleteUser(stale);

        final UserRegistrationService.PurgeReport report = registration.purgeUnverified();

        assertEquals(1, report.failed());
        assertTrue(exists(stale));
        assertTrue(pending(stale));
    }

    // ---------------------------------------------------------------------
    //  Limiteur de débit
    // ---------------------------------------------------------------------

    @Test
    void rate_limiter_blocks_beyond_the_maximum_within_a_window() {
        final String key = "test:" + UUID.randomUUID();

        assertTrue(rateLimiter.tryAcquire(key, 2, Duration.ofMinutes(1)));
        assertTrue(rateLimiter.tryAcquire(key, 2, Duration.ofMinutes(1)));
        assertFalse(rateLimiter.tryAcquire(key, 2, Duration.ofMinutes(1)));
    }

    @Test
    void duplicate_email_is_rejected_before_calling_keycloak() {
        final String email = uniqueEmail();
        when(identity.createUser(any())).thenReturn(UUID.randomUUID().toString());
        given().contentType(ContentType.JSON).body(payload(email, uniquePhone()))
                .when().post(REGISTER).then().statusCode(201);

        given().contentType(ContentType.JSON).body(payload(email, uniquePhone()))
                .when().post(REGISTER).then()
                .statusCode(409)
                .body("code", is("EMAIL_ALREADY_REGISTERED"));

        // Un seul appel : celui de la première inscription.
        verify(identity).createUser(any());
    }

    @Test
    void duplicate_phone_is_rejected() {
        final String phone = uniquePhone();
        when(identity.createUser(any())).thenReturn(UUID.randomUUID().toString());
        given().contentType(ContentType.JSON).body(payload(uniqueEmail(), phone))
                .when().post(REGISTER).then().statusCode(201);

        given().contentType(ContentType.JSON).body(payload(uniqueEmail(), phone))
                .when().post(REGISTER).then()
                .statusCode(409)
                .body("code", is("PHONE_ALREADY_REGISTERED"));
    }

    @Test
    void keycloak_conflict_is_reported_and_nothing_is_persisted() {
        final String email = uniqueEmail();
        when(identity.createUser(any())).thenThrow(RegistrationConflictException.emailTaken());

        given().contentType(ContentType.JSON).body(payload(email, uniquePhone()))
                .when().post(REGISTER).then()
                .statusCode(409)
                .body("code", is("EMAIL_ALREADY_REGISTERED"));

        assertTrue(QuarkusTransaction.requiringNew().call(() -> users.findByEmail(email).isEmpty()));
    }

    @Test
    void password_refused_by_realm_policy_is_a_422() {
        when(identity.createUser(any())).thenThrow(new WeakPasswordException("trop faible"));

        given().contentType(ContentType.JSON).body(payload(uniqueEmail(), uniquePhone()))
                .when().post(REGISTER).then()
                .statusCode(422)
                .body("code", is("WEAK_PASSWORD"));
    }

    @Test
    void keycloak_account_is_removed_when_database_insert_fails() {
        // Deux inscriptions distinctes à qui Keycloak attribue le même identifiant :
        // la seconde viole l'unicité de keycloak_id après la création Keycloak.
        final String keycloakId = UUID.randomUUID().toString();
        when(identity.createUser(any())).thenReturn(keycloakId);
        given().contentType(ContentType.JSON).body(payload(uniqueEmail(), uniquePhone()))
                .when().post(REGISTER).then().statusCode(201);
        verify(identity, never()).deleteUser(anyString());

        final String secondEmail = uniqueEmail();
        given().contentType(ContentType.JSON).body(payload(secondEmail, uniquePhone()))
                .when().post(REGISTER).then().statusCode(500);

        verify(identity).deleteUser(keycloakId);
        assertTrue(QuarkusTransaction.requiringNew().call(() -> users.findByEmail(secondEmail).isEmpty()));
    }

    @Test
    void invalid_payload_is_a_400_with_field_violations() {
        final Map<String, String> body = Map.of(
                "email", "pas-un-email",
                "phone", "0612345678",
                "firstName", "A",
                "lastName", "B",
                "countryOfResidence", "FR",
                "password", "court");

        given().contentType(ContentType.JSON).body(body)
                .when().post(REGISTER).then()
                .statusCode(400)
                .body("code", is("VALIDATION_ERROR"))
                .body("violations.field", hasItem("email"))
                .body("violations.field", hasItem("phone"))
                .body("violations.field", hasItem("password"));

        verify(identity, never()).createUser(any());
    }
}
