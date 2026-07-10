package com.zalamtech.diama.domain.user;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository Panache des appareils enregistrés (tokens push). */
@ApplicationScoped
public class UserDeviceRepository implements PanacheRepositoryBase<UserDevice, UUID> {

    /**
     * Appareil d'un utilisateur identifié par son token push. Sert à
     * l'enregistrement idempotent (réactivation plutôt que doublon).
     */
    public Optional<UserDevice> findByUserIdAndToken(UUID userId, String deviceToken) {
        return find("userId = ?1 and deviceToken = ?2", userId, deviceToken).firstResultOptional();
    }

    /** Appareils actifs d'un utilisateur (destinataires push). */
    public List<UserDevice> findActiveByUserId(UUID userId) {
        return find("userId = ?1 and isActive = true", userId).list();
    }
}
