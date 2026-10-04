package com.zalamtech.diama.domain.audit;

import com.zalamtech.diama.common.dto.ActiveSessionDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Lecture de la vue {@code v_active_sessions} (monitoring backoffice temps réel).
 *
 * <p>Requête native plutôt qu'entité Panache : {@code v_active_sessions} est une
 * vue SQL en lecture seule (jointure {@code user_sessions} / {@code users} /
 * {@code kyc_profiles}), sans cycle de vie JPA à gérer.</p>
 */
@ApplicationScoped
public class ActiveSessionRepository {

    // host() : la colonne inet arrive sinon en java.net.InetAddress, incastable en String.
    private static final String QUERY = """
            select id, keycloak_session_id, client, host(ip_address) as ip_address,
                   started_at, last_seen_at, email, keycloak_id, kyc_level
            from v_active_sessions
            order by last_seen_at desc
            """;

    @Inject
    EntityManager em;

    @SuppressWarnings("unchecked")
    public List<ActiveSessionDto> findActive() {
        final List<Tuple> rows = em.createNativeQuery(QUERY, Tuple.class).getResultList();
        return rows.stream().map(ActiveSessionRepository::toDto).toList();
    }

    private static ActiveSessionDto toDto(Tuple row) {
        return new ActiveSessionDto(
                row.get("id", UUID.class),
                row.get("keycloak_session_id", String.class),
                row.get("client", String.class),
                row.get("ip_address", String.class),
                toOffset(row.get("started_at")),
                toOffset(row.get("last_seen_at")),
                row.get("email", String.class),
                row.get("keycloak_id", String.class),
                row.get("kyc_level", Short.class));
    }

    /** timestamptz d'une requête native : Instant ou OffsetDateTime selon le mapping Hibernate. */
    private static OffsetDateTime toOffset(Object value) {
        return switch (value) {
            case null -> null;
            case OffsetDateTime odt -> odt;
            case java.time.Instant instant -> instant.atOffset(java.time.ZoneOffset.UTC);
            default -> throw new IllegalStateException(
                    "Type timestamp inattendu : " + value.getClass().getName());
        };
    }
}
