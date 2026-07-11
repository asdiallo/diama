package com.zalamtech.diama.domain.audit;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/** Repository Panache des événements d'audit (PK UUID). */
@ApplicationScoped
public class AuditEventRepository implements PanacheRepositoryBase<AuditEvent, UUID> {

    /** Liste filtrée/paginée, les événements les plus récents d'abord. */
    public java.util.List<AuditEvent> search(UUID userId, String eventType, OffsetDateTime from, OffsetDateTime to,
                                              int page, int size) {
        final Filter filter = Filter.of(userId, eventType, from, to);
        return find(filter.hql(), Sort.by("occurredAt").descending(), filter.params())
                .page(Page.of(page, size))
                .list();
    }

    public long count(UUID userId, String eventType, OffsetDateTime from, OffsetDateTime to) {
        final Filter filter = Filter.of(userId, eventType, from, to);
        return count(filter.hql(), filter.params());
    }

    /** Flux (curseur) des événements survenus depuis {@code since} — export CSV. */
    public Stream<AuditEvent> streamSince(OffsetDateTime since) {
        return find("occurredAt >= ?1", Sort.by("occurredAt").descending(), since).stream();
    }

    private record Filter(String hql, Map<String, Object> params) {
        static Filter of(UUID userId, String eventType, OffsetDateTime from, OffsetDateTime to) {
            final StringBuilder hql = new StringBuilder("1=1");
            final Map<String, Object> params = new LinkedHashMap<>();
            if (userId != null) {
                hql.append(" and userId = :userId");
                params.put("userId", userId);
            }
            if (eventType != null) {
                hql.append(" and eventType = :eventType");
                params.put("eventType", eventType);
            }
            if (from != null) {
                hql.append(" and occurredAt >= :from");
                params.put("from", from);
            }
            if (to != null) {
                hql.append(" and occurredAt <= :to");
                params.put("to", to);
            }
            return new Filter(hql.toString(), params);
        }
    }
}
