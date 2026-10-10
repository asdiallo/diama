package com.zalamtech.diama.domain.user;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import org.hibernate.query.NativeQuery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository Panache des sessions Keycloak (PK UUID). */
@ApplicationScoped
public class UserSessionRepository implements PanacheRepositoryBase<UserSession, UUID> {

    public Optional<UserSession> findByKeycloakSessionId(String keycloakSessionId) {
        return find("keycloakSessionId", keycloakSessionId).firstResultOptional();
    }

    public boolean existsByKeycloakSessionId(String keycloakSessionId) {
        return count("keycloakSessionId", keycloakSessionId) > 0;
    }

    // id, is_active, started_at, last_seen_at : DEFAULT de la table.
    private static final String INSERT_IF_ABSENT = """
            insert into user_sessions (user_id, keycloak_session_id, client, ip_address, user_agent)
            values (:userId, :sid, cast(:client as session_client), cast(:ip as inet), :userAgent)
            on conflict (keycloak_session_id) do nothing
            """;

    /**
     * Insère la session sauf si le {@code sid} existe déjà. Atomique côté PostgreSQL :
     * deux premières requêtes concurrentes ne lèvent pas de violation d'unicité.
     *
     * @return {@code true} si la session a été créée
     */
    public boolean insertIfAbsent(UUID userId, String keycloakSessionId, SessionClient client,
                                  String ipAddress, String userAgent) {
        return getEntityManager().createNativeQuery(INSERT_IF_ABSENT)
                .unwrap(NativeQuery.class)
                .setParameter("userId", userId, UUID.class)
                .setParameter("sid", keycloakSessionId, String.class)
                .setParameter("client", client.getDbValue(), String.class)
                // Typés explicitement : un null non typé n'est pas castable en inet.
                .setParameter("ip", ipAddress, String.class)
                .setParameter("userAgent", userAgent, String.class)
                .executeUpdate() > 0;
    }

    /** Sessions actives d'un utilisateur, les plus récemment actives d'abord. */
    public List<UserSession> findActiveByUserId(UUID userId) {
        return find("userId = ?1 and isActive = true", Sort.by("lastSeenAt").descending(), userId).list();
    }
}
