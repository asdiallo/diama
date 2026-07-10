package com.zalamtech.diama.domain.user;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

/** Repository Panache des utilisateurs finaux (PK UUID). */
@ApplicationScoped
public class UserRepository implements PanacheRepositoryBase<User, UUID> {

    public Optional<User> findByKeycloakId(String keycloakId) {
        return find("keycloakId", keycloakId).firstResultOptional();
    }

    public Optional<User> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }

    public Optional<User> findByPhone(String phone) {
        return find("phone", phone).firstResultOptional();
    }

    public Optional<User> findBySwanIban(String swanIban) {
        return find("swanIban", swanIban).firstResultOptional();
    }

    public Optional<User> findBySwanAccountId(String swanAccountId) {
        return find("swanAccountId", swanAccountId).firstResultOptional();
    }
}
