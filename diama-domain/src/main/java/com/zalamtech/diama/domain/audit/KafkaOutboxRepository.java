package com.zalamtech.diama.domain.audit;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Repository Panache de l'outbox Kafka (PK UUID). */
@ApplicationScoped
public class KafkaOutboxRepository implements PanacheRepositoryBase<KafkaOutbox, UUID> {

    /**
     * Prochain lot d'entrées PENDING à publier, les plus anciennes d'abord.
     * <p>Pour un traitement concurrent, envisager un {@code FOR UPDATE SKIP LOCKED}
     * (via requête native) afin d'éviter que deux workers ne traitent le même lot.</p>
     */
    public List<KafkaOutbox> findPendingForProcessing(int batchSize) {
        return find("status", Sort.by("createdAt").ascending(), OutboxStatus.PENDING)
                .range(0, Math.max(0, batchSize - 1))
                .list();
    }

    /** Marque une entrée comme publiée. */
    @Transactional
    public void markPublished(UUID id) {
        update("status = ?1, processedAt = ?2 where id = ?3",
                OutboxStatus.PUBLISHED, OffsetDateTime.now(ZoneOffset.UTC), id);
    }

    /** Marque une entrée en échec et incrémente le compteur de tentatives. */
    @Transactional
    public void markFailed(UUID id, String errorMessage) {
        update("status = ?1, retryCount = retryCount + 1, errorMessage = ?2 where id = ?3",
                OutboxStatus.FAILED, errorMessage, id);
    }
}
