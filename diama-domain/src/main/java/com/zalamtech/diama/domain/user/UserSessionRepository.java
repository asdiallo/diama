package com.zalamtech.diama.domain.user;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
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

    /** Sessions actives d'un utilisateur, les plus récemment actives d'abord. */
    public List<UserSession> findActiveByUserId(UUID userId) {
        return find("userId = ?1 and isActive = true", Sort.by("lastSeenAt").descending(), userId).list();
    }
}
