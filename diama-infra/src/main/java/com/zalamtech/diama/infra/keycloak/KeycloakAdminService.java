package com.zalamtech.diama.infra.keycloak;

import com.zalamtech.diama.common.exception.RegistrationConflictException;
import com.zalamtech.diama.common.exception.WeakPasswordException;
import com.zalamtech.diama.domain.user.EmailVerificationState;
import com.zalamtech.diama.domain.user.NewIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.idm.UserSessionRepresentation;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pilotage du realm Keycloak via l'Admin API (client {@code quarkus-api},
 * grant {@code client_credentials}). Utilisé notamment par le consumer
 * {@code kyc-events} pour propager {@code kyc_level} vers l'attribut Keycloak.
 */
@Slf4j
@ApplicationScoped
public class KeycloakAdminService {

    @Inject
    Keycloak keycloak;

    @ConfigProperty(name = "quarkus.keycloak.admin-client.realm", defaultValue = "diama-transfer-app")
    String realm;

    private static final String VERIFY_EMAIL = "VERIFY_EMAIL";

    /** Client au nom duquel le lien de confirmation est émis (doit autoriser {@code redirect-uri}). */
    @ConfigProperty(name = "diama.registration.verification.client-id", defaultValue = "mobile-app")
    String verificationClientId;

    /** Retour vers l'application après confirmation ; à déclarer dans les « Valid redirect URIs » du client. */
    @ConfigProperty(name = "diama.registration.verification.redirect-uri", defaultValue = "diama://email-verified")
    String verificationRedirectUri;

    @ConfigProperty(name = "diama.registration.verification.link-lifespan", defaultValue = "PT24H")
    Duration verificationLinkLifespan;

    private RealmResource realm() {
        return keycloak.realm(realm);
    }

    /** {@code keycloakId} = {@code sub} du JWT = identifiant utilisateur Keycloak. */
    public void updateUserAttribute(String keycloakId, String attr, String value) {
        final UserResource userResource = realm().users().get(keycloakId);
        final UserRepresentation rep = userResource.toRepresentation();
        Map<String, List<String>> attributes = rep.getAttributes();
        if (attributes == null) {
            attributes = new HashMap<>();
            rep.setAttributes(attributes);
        }
        attributes.put(attr, List.of(value));
        userResource.update(rep);
        log.info("Keycloak: attribut {}={} mis à jour pour {}", attr, value, keycloakId);
    }

    /** Propage le niveau KYC dans le claim custom {@code kyc_level}. */
    public void updateKycLevel(String keycloakId, int level) {
        updateUserAttribute(keycloakId, "kyc_level", String.valueOf(level));
    }

    public void assignRole(String keycloakId, String role) {
        final RoleRepresentation roleRep = realm().roles().get(role).toRepresentation();
        realm().users().get(keycloakId).roles().realmLevel().add(List.of(roleRep));
        log.info("Keycloak: rôle {} assigné à {}", role, keycloakId);
    }

    public void revokeRole(String keycloakId, String role) {
        final RoleRepresentation roleRep = realm().roles().get(role).toRepresentation();
        realm().users().get(keycloakId).roles().realmLevel().remove(List.of(roleRep));
        log.info("Keycloak: rôle {} révoqué pour {}", role, keycloakId);
    }

    public List<UserSessionRepresentation> getUserSessions(String keycloakId) {
        return realm().users().get(keycloakId).getUserSessions();
    }

    /** Invalide une session Keycloak (déconnexion forcée). */
    public void invalidateSession(String sessionId) {
        realm().deleteSession(sessionId, false);
        log.info("Keycloak: session {} invalidée", sessionId);
    }

    /**
     * Crée un utilisateur final (inscription mobile). L'email fait office de
     * {@code username} ; le rôle {@code user} est attribué par les rôles par
     * défaut du realm. Requiert le rôle {@code manage-users} sur le compte de
     * service {@code quarkus-api}.
     *
     * @return identifiant Keycloak du compte créé ({@code sub} des JWT)
     */
    public String createUser(NewIdentity identity) {
        final CredentialRepresentation password = new CredentialRepresentation();
        password.setType(CredentialRepresentation.PASSWORD);
        password.setValue(identity.password());
        password.setTemporary(false);

        final UserRepresentation rep = new UserRepresentation();
        rep.setUsername(identity.email());
        rep.setEmail(identity.email());
        rep.setFirstName(identity.firstName());
        rep.setLastName(identity.lastName());
        rep.setEnabled(true);
        rep.setEmailVerified(false);
        // Même attribut que celui lu par le mapper du claim `phone_number`.
        rep.setAttributes(new HashMap<>(Map.of(
                "phoneNumber", List.of(identity.phone()),
                "phoneNumberVerified", List.of("false"))));
        rep.setCredentials(List.of(password));
        // Action requise : Keycloak refuse d'émettre un jeton tant que l'email
        // n'est pas confirmé, que l'option « Verify email » du realm soit active ou non.
        rep.setRequiredActions(List.of(VERIFY_EMAIL));

        try (Response response = realm().users().create(rep)) {
            final int status = response.getStatus();
            if (status == Response.Status.CREATED.getStatusCode()) {
                final String keycloakId = CreatedResponseUtil.getCreatedId(response);
                log.info("Keycloak: utilisateur {} créé", keycloakId);
                return keycloakId;
            }
            if (status == Response.Status.CONFLICT.getStatusCode()) {
                throw RegistrationConflictException.emailTaken();
            }
            // Le corps peut répéter des données saisies : on ne journalise que le statut.
            log.warn("Keycloak: création d'utilisateur refusée (HTTP {})", status);
            if (status == Response.Status.BAD_REQUEST.getStatusCode()) {
                // Keycloak répond 400 quand le mot de passe viole la politique du realm.
                throw new WeakPasswordException("Ce mot de passe ne respecte pas les règles de sécurité");
            }
            throw new IllegalStateException("Création du compte Keycloak impossible (HTTP " + status + ")");
        }
    }

    public void deleteUser(String keycloakId) {
        realm().users().get(keycloakId).remove();
        log.info("Keycloak: utilisateur {} supprimé", keycloakId);
    }

    /** Requiert un SMTP configuré sur le realm ; Keycloak répond 500 sinon. */
    public void sendVerificationEmail(String keycloakId) {
        realm().users().get(keycloakId).executeActionsEmail(
                verificationClientId,
                verificationRedirectUri,
                (int) verificationLinkLifespan.toSeconds(),
                List.of(VERIFY_EMAIL));
        log.info("Keycloak: email de confirmation envoyé à {}", keycloakId);
    }

    public EmailVerificationState emailVerificationState(String keycloakId) {
        try {
            final UserRepresentation rep = realm().users().get(keycloakId).toRepresentation();
            return Boolean.TRUE.equals(rep.isEmailVerified())
                    ? EmailVerificationState.VERIFIED
                    : EmailVerificationState.PENDING;
        } catch (NotFoundException e) {
            return EmailVerificationState.MISSING;
        }
    }
}
