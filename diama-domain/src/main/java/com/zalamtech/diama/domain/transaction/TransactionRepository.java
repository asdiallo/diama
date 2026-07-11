package com.zalamtech.diama.domain.transaction;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.Tuple;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Repository Panache des transactions (PK UUID). */
@ApplicationScoped
public class TransactionRepository implements PanacheRepositoryBase<Transaction, UUID> {

    private static final String HOURLY_VOLUME_QUERY = """
            select date_trunc('hour', created_at) as hour, count(*) as tx_count, coalesce(sum(source_amount), 0) as volume
            from transactions
            where created_at >= ?1
            group by 1
            order by 1
            """;

    /** Transactions d'un utilisateur, paginées, les plus récentes d'abord. */
    public List<Transaction> findByUserIdPaginated(UUID userId, int pageIndex, int pageSize) {
        return find("userId", Sort.by("createdAt").descending(), userId)
                .page(Page.of(pageIndex, pageSize))
                .list();
    }

    public Optional<Transaction> findByThunesExternalId(String thunesExternalId) {
        return find("thunesExternalId", thunesExternalId).firstResultOptional();
    }

    public Optional<Transaction> findBySwanTransferId(String swanTransferId) {
        return find("swanTransferId", swanTransferId).firstResultOptional();
    }

    /** Transaction {@code INITIATED} d'un utilisateur pour un montant donné, la plus ancienne d'abord. */
    public Optional<Transaction> findFirstInitiatedByUserAndAmount(UUID userId, BigDecimal amount) {
        return find("userId = ?1 and status = ?2 and sourceAmount = ?3",
                Sort.by("createdAt").ascending(), userId, TxStatus.INITIATED, amount)
                .firstResultOptional();
    }

    /** Transactions dans un statut donné et plus anciennes qu'une borne (jobs de reprise). */
    public List<Transaction> findByStatusAndCreatedAtBefore(TxStatus status, OffsetDateTime before) {
        return list("status = ?1 and createdAt < ?2", status, before);
    }

    /** Nombre de transactions initiées par l'utilisateur depuis le 1er du mois courant. */
    public long countByUserIdThisMonth(UUID userId) {
        return count("userId = ?1 and createdAt >= ?2", userId, startOfCurrentMonth());
    }

    // ---------------------------------------------------------------------
    //  Backoffice
    // ---------------------------------------------------------------------

    /** Recherche filtrée/paginée (backoffice), triée selon {@code sort}. */
    public List<Transaction> searchAdmin(UUID userId, TxStatus status, String sourceCurrency, String destCurrency,
                                         OffsetDateTime from, OffsetDateTime to, Sort sort, int page, int size) {
        final AdminFilter filter = AdminFilter.of(userId, status, sourceCurrency, destCurrency, from, to);
        return find(filter.hql(), sort, filter.params())
                .page(Page.of(page, size))
                .list();
    }

    public long countAdmin(UUID userId, TxStatus status, String sourceCurrency, String destCurrency,
                           OffsetDateTime from, OffsetDateTime to) {
        final AdminFilter filter = AdminFilter.of(userId, status, sourceCurrency, destCurrency, from, to);
        return count(filter.hql(), filter.params());
    }

    /** Somme des montants envoyés (toutes devises confondues), transactions créées depuis {@code since}. */
    public BigDecimal sumSourceAmountByCreatedAfter(OffsetDateTime since) {
        return getEntityManager()
                .createQuery("select coalesce(sum(t.sourceAmount), 0) from Transaction t where t.createdAt >= ?1", BigDecimal.class)
                .setParameter(1, since)
                .getSingleResult();
    }

    /** Somme des frais perçus pour un statut donné, transactions créées depuis {@code since}. */
    public BigDecimal sumFeeAmountByStatusAndCreatedAfter(TxStatus status, OffsetDateTime since) {
        return getEntityManager()
                .createQuery("select coalesce(sum(t.feeAmount), 0) from Transaction t where t.status = ?1 and t.createdAt >= ?2",
                        BigDecimal.class)
                .setParameter(1, status)
                .setParameter(2, since)
                .getSingleResult();
    }

    /** Volume de transferts initiés, groupé par heure, depuis {@code since}. */
    @SuppressWarnings("unchecked")
    public List<Tuple> hourlyVolumeSince(OffsetDateTime since) {
        return getEntityManager()
                .createNativeQuery(HOURLY_VOLUME_QUERY, Tuple.class)
                .setParameter(1, since)
                .getResultList();
    }

    private static OffsetDateTime startOfCurrentMonth() {
        return OffsetDateTime.now(ZoneOffset.UTC)
                .with(TemporalAdjusters.firstDayOfMonth())
                .toLocalDate()
                .atStartOfDay()
                .atOffset(ZoneOffset.UTC);
    }

    private record AdminFilter(String hql, Map<String, Object> params) {
        static AdminFilter of(UUID userId, TxStatus status, String sourceCurrency, String destCurrency,
                              OffsetDateTime from, OffsetDateTime to) {
            final StringBuilder hql = new StringBuilder("1=1");
            final Map<String, Object> params = new LinkedHashMap<>();
            if (userId != null) {
                hql.append(" and userId = :userId");
                params.put("userId", userId);
            }
            if (status != null) {
                hql.append(" and status = :status");
                params.put("status", status);
            }
            if (sourceCurrency != null) {
                hql.append(" and sourceCurrency = :sourceCurrency");
                params.put("sourceCurrency", sourceCurrency);
            }
            if (destCurrency != null) {
                hql.append(" and destCurrency = :destCurrency");
                params.put("destCurrency", destCurrency);
            }
            if (from != null) {
                hql.append(" and createdAt >= :from");
                params.put("from", from);
            }
            if (to != null) {
                hql.append(" and createdAt <= :to");
                params.put("to", to);
            }
            return new AdminFilter(hql.toString(), params);
        }
    }
}
