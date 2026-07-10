package com.zalamtech.diama.domain.audit;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Repository Panache des notifications sortantes (PK UUID). */
@ApplicationScoped
public class NotificationRepository implements PanacheRepositoryBase<Notification, UUID> {

    /** Marque une notification comme envoyée avec succès. */
    @Transactional
    public void markSent(UUID id, String providerRef) {
        update("status = ?1, providerRef = ?2, sentAt = ?3 where id = ?4",
                NotificationStatus.SENT, providerRef, OffsetDateTime.now(ZoneOffset.UTC), id);
    }

    /** Marque une notification en échec et incrémente le compteur de tentatives. */
    @Transactional
    public void markFailed(UUID id) {
        update("status = ?1, retryCount = retryCount + 1 where id = ?2", NotificationStatus.FAILED, id);
    }
}
