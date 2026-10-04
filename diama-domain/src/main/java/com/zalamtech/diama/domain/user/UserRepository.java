package com.zalamtech.diama.domain.user;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** Inscriptions non confirmées antérieures à {@code cutoff}, les plus anciennes d'abord. */
    public List<User> findUnverifiedCreatedBefore(OffsetDateTime cutoff, int limit) {
        return find("emailVerificationPending = true and createdAt < ?1", Sort.by("createdAt"), cutoff)
                .page(Page.of(0, limit))
                .list();
    }

    public Optional<User> findBySwanIban(String swanIban) {
        return find("swanIban", swanIban).firstResultOptional();
    }

    public Optional<User> findBySwanAccountId(String swanAccountId) {
        return find("swanAccountId", swanAccountId).firstResultOptional();
    }

    /** Recherche filtrée/paginée (backoffice) : statut exact, texte libre sur email/téléphone/nom. */
    public List<User> searchAdmin(UserStatus status, String q, Sort sort, int page, int size) {
        final AdminFilter filter = AdminFilter.of(status, q);
        return find(filter.hql(), sort, filter.params())
                .page(Page.of(page, size))
                .list();
    }

    public long countAdmin(UserStatus status, String q) {
        final AdminFilter filter = AdminFilter.of(status, q);
        return count(filter.hql(), filter.params());
    }

    private record AdminFilter(String hql, Map<String, Object> params) {
        static AdminFilter of(UserStatus status, String q) {
            final StringBuilder hql = new StringBuilder("1=1");
            final Map<String, Object> params = new LinkedHashMap<>();
            if (status != null) {
                hql.append(" and status = :status");
                params.put("status", status);
            }
            if (q != null && !q.isBlank()) {
                hql.append(" and (lower(email) like :q or lower(firstName) like :q "
                        + "or lower(lastName) like :q or phone like :qRaw)");
                params.put("q", "%" + q.toLowerCase(java.util.Locale.ROOT) + "%");
                params.put("qRaw", "%" + q + "%");
            }
            return new AdminFilter(hql.toString(), params);
        }
    }
}
