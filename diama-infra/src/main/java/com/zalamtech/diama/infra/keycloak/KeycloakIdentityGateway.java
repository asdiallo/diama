package com.zalamtech.diama.infra.keycloak;

import com.zalamtech.diama.domain.user.EmailVerificationState;
import com.zalamtech.diama.domain.user.IdentityGateway;
import com.zalamtech.diama.domain.user.NewIdentity;
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

    @Override
    public void invalidateSession(String keycloakSessionId) {
        keycloakAdmin.invalidateSession(keycloakSessionId);
    }

    @Override
    public String createUser(NewIdentity identity) {
        return keycloakAdmin.createUser(identity);
    }

    @Override
    public void deleteUser(String keycloakId) {
        keycloakAdmin.deleteUser(keycloakId);
    }

    @Override
    public void sendVerificationEmail(String keycloakId) {
        keycloakAdmin.sendVerificationEmail(keycloakId);
    }

    @Override
    public EmailVerificationState emailVerificationState(String keycloakId) {
        return keycloakAdmin.emailVerificationState(keycloakId);
    }
}
