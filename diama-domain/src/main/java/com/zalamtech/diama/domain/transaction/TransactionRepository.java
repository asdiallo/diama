package com.zalamtech.diama.domain.transaction;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository Panache des transactions (PK UUID). */
@ApplicationScoped
public class TransactionRepository implements PanacheRepositoryBase<Transaction, UUID> {

    /** Transactions d'un utilisateur, paginées, les plus récentes d'abord. */
    public List<Transaction> findByUserIdPaginated(UUID userId, int pageIndex, int pageSize) {
        return find("userId", Sort.by("createdAt").descending(), userId)
                .page(Page.of(pageIndex, pageSize))
                .list();
    }

    public Optional<Transaction> findByThunesExternalId(String thunesExternalId) {
        return find("thunesExternalId", thunesExternalId).firstResultOptional();
    }

    /** Transactions dans un statut donné et plus anciennes qu'une borne (jobs de reprise). */
    public List<Transaction> findByStatusAndCreatedAtBefore(TxStatus status, OffsetDateTime before) {
        return list("status = ?1 and createdAt < ?2", status, before);
    }

    /** Nombre de transactions initiées par l'utilisateur depuis le 1er du mois courant. */
    public long countByUserIdThisMonth(UUID userId) {
        return count("userId = ?1 and createdAt >= ?2", userId, startOfCurrentMonth());
    }

    private static OffsetDateTime startOfCurrentMonth() {
        return OffsetDateTime.now(ZoneOffset.UTC)
                .with(TemporalAdjusters.firstDayOfMonth())
                .toLocalDate()
                .atStartOfDay()
                .atOffset(ZoneOffset.UTC);
    }
}
