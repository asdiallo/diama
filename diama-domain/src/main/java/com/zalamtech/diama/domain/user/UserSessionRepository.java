package com.zalamtech.diama.domain.user;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

/** Repository Panache des sessions Keycloak (PK UUID). */
@ApplicationScoped
public class UserSessionRepository implements PanacheRepositoryBase<UserSession, UUID> {

    public Optional<UserSession> findByKeycloakSessionId(String keycloakSessionId) {
        return find("keycloakSessionId", keycloakSessionId).firstResultOptional();
    }

    public boolean existsByKeycloakSessionId(String keycloakSessionId) {
        return count("keycloakSessionId", keycloakSessionId) > 0;
    }
}
