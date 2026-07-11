package com.zalamtech.diama.domain.transaction;

import com.zalamtech.diama.common.dto.CorridorStatsDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;

import java.math.BigDecimal;
import java.util.List;

/** Lecture de la vue {@code v_corridor_stats} (30 jours glissants, tableau de bord backoffice). */
@ApplicationScoped
public class CorridorStatsRepository {

    private static final String QUERY = """
            select source_currency, dest_currency, total_transactions, total_volume_eur,
                   avg_amount, total_fees, successful, failed, success_rate_pct
            from v_corridor_stats
            order by total_volume_eur desc
            """;

    @Inject
    EntityManager em;

    @SuppressWarnings("unchecked")
    public List<CorridorStatsDto> findAll() {
        final List<Tuple> rows = em.createNativeQuery(QUERY, Tuple.class).getResultList();
        return rows.stream().map(CorridorStatsRepository::toDto).toList();
    }

    private static CorridorStatsDto toDto(Tuple row) {
        return new CorridorStatsDto(
                row.get("source_currency", String.class),
                row.get("dest_currency", String.class),
                row.get("total_transactions", Number.class).longValue(),
                row.get("total_volume_eur", BigDecimal.class),
                row.get("avg_amount", BigDecimal.class),
                row.get("total_fees", BigDecimal.class),
                row.get("successful", Number.class).longValue(),
                row.get("failed", Number.class).longValue(),
                row.get("success_rate_pct", BigDecimal.class));
    }
}
