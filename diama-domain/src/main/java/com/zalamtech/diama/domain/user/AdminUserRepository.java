package com.zalamtech.diama.domain.user;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

/** Repository Panache des agents backoffice (PK UUID). */
@ApplicationScoped
public class AdminUserRepository implements PanacheRepositoryBase<AdminUser, UUID> {

    public Optional<AdminUser> findByKeycloakId(String keycloakId) {
        return find("keycloakId", keycloakId).firstResultOptional();
    }
}
