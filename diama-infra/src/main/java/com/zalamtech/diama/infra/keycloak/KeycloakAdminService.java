package com.zalamtech.diama.infra.keycloak;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.idm.UserSessionRepresentation;

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

    @ConfigProperty(name = "quarkus.keycloak.admin-client.realm", defaultValue = "transfer-app")
    String realm;

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
}
