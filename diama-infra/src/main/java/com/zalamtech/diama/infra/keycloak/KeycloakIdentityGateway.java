package com.zalamtech.diama.infra.keycloak;

import com.zalamtech.diama.domain.user.IdentityGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Adaptateur Keycloak : implémente le port {@link IdentityGateway} (architecture hexagonale). */
@ApplicationScoped
public class KeycloakIdentityGateway implements IdentityGateway {

    @Inject
    KeycloakAdminService keycloakAdmin;

    @Override
    public void updateKycLevel(String keycloakId, int level) {
        keycloakAdmin.updateKycLevel(keycloakId, level);
    }
}
