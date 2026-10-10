package com.zalamtech.diama.domain.user;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Synchronise l'agent backoffice ({@code admin_users}) avec Keycloak, source de
 * vérité des droits : la ligne est créée à la première action de l'agent à partir
 * du JWT, puis son rôle suit celui du realm. Sans elle, les actions tracées par
 * {@code admin_id} seraient refusées à tout agent non inséré à la main.
 *
 * <p>Un agent désactivé ({@code is_active = false}) reste refusé : la
 * désactivation locale prime sur les rôles Keycloak.</p>
 *
 * <p><b>Concurrence</b> : même stratégie que {@link UserSyncService} — la
 * contrainte unique sur {@code keycloak_id} arbitre, le perdant réessaie.</p>
 */
@Slf4j
@ApplicationScoped
public class AdminUserSyncService {

    /** Du plus au moins privilégié : le premier rôle porté par le JWT l'emporte. */
    private static final List<AdminRole> PRECEDENCE =
            List.of(AdminRole.SUPER_ADMIN, AdminRole.ADMIN, AdminRole.OPS_AGENT, AdminRole.KYC_AGENT);

    @Inject
    AdminUserRepository adminUsers;

    /** Auto-injection : nécessaire pour que l'intercepteur {@code @Transactional} s'applique. */
    @Inject
    AdminUserSyncService self;

    /**
     * Retourne l'agent actif lié au JWT, en le créant au besoin.
     *
     * @param roles rôles realm du JWT
     * @return vide si le JWT ne porte aucun rôle backoffice, si l'email manque
     *         (colonne NOT NULL) ou si l'agent est désactivé
     */
    public Optional<AdminUser> getOrProvision(String keycloakId, String email, String fullName, Set<String> roles) {
        final Optional<AdminRole> role = highestRole(roles);
        if (keycloakId == null || role.isEmpty()) {
            return Optional.empty();
        }
        try {
            return self.syncInTx(keycloakId, email, fullName, role.get());
        } catch (PersistenceException e) {
            if (!isDuplicateKey(e)) {
                throw e;
            }
            // Course perdue : la ligne vient d'être committée par une autre requête.
            return self.syncInTx(keycloakId, email, fullName, role.get());
        }
    }

    @Transactional
    Optional<AdminUser> syncInTx(String keycloakId, String email, String fullName, AdminRole role) {
        final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        final AdminUser existing = adminUsers.findByKeycloakId(keycloakId).orElse(null);
        if (existing != null) {
            if (!Boolean.TRUE.equals(existing.getIsActive())) {
                return Optional.empty();
            }
            // Entité managée : les setters sont flushés en fin de transaction.
            if (existing.getRole() != role) {
                log.info("Agent {} : rôle {} → {} (Keycloak)", keycloakId, existing.getRole(), role);
                existing.setRole(role);
            }
            existing.setLastLoginAt(now);
            return Optional.of(existing);
        }

        if (email == null || email.isBlank()) {
            log.warn("Agent {} : claim email absent, provisionnement admin_users impossible", keycloakId);
            return Optional.empty();
        }
        final AdminUser created = AdminUser.builder()
                .keycloakId(keycloakId)
                .email(email)
                .fullName(fullName == null || fullName.isBlank() ? email : fullName)
                .role(role)
                .lastLoginAt(now)
                .build();
        adminUsers.persist(created);
        // Flush ici : une violation de contrainte remonte dans cette transaction (rattrapable).
        adminUsers.flush();
        log.info("Agent backoffice provisionné : {} ({})", keycloakId, role.getDbValue());
        return Optional.of(created);
    }

    static Optional<AdminRole> highestRole(Set<String> roles) {
        if (roles == null) {
            return Optional.empty();
        }
        return PRECEDENCE.stream().filter(r -> roles.contains(r.getDbValue())).findFirst();
    }

    private static boolean isDuplicateKey(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof ConstraintViolationException) {
                return true;
            }
        }
        return false;
    }
}
