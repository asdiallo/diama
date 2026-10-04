package com.zalamtech.diama.api.security;

import com.zalamtech.diama.common.dto.AuditContext;
import com.zalamtech.diama.domain.user.AdminUser;
import com.zalamtech.diama.domain.user.AdminUserRepository;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

/**
 * Accès centralisé au contexte de sécurité de la requête courante,
 * dérivé du JWT Keycloak (realm diama-transfer-app).
 *
 * <p>Note : {@link #getCurrentUserId()} renvoie le {@code sub} du JWT
 * (= {@code keycloak_id}), <b>pas</b> la PK interne {@code users.id}.</p>
 */
@ApplicationScoped
public class SecurityContext {

    @Inject
    JsonWebToken jwt;

    @Inject
    SecurityIdentity identity;

    @Inject
    AdminUserRepository adminUsers;

    @Inject
    RoutingContext routingContext;

    /** {@code sub} du JWT (identifiant Keycloak de l'utilisateur), sous forme d'UUID. */
    public UUID getCurrentUserId() {
        final String sub = jwt.getSubject();
        return sub == null ? null : UUID.fromString(sub);
    }

    /** Niveau KYC issu du claim custom {@code kyc_level} (0 si absent / illisible). */
    public int getKycLevel() {
        final Object claim = jwt.getClaim("kyc_level");
        if (claim == null) {
            return 0;
        }
        if (claim instanceof jakarta.json.JsonNumber jn) {
            return jn.intValue();
        }
        if (claim instanceof Number n) {
            return n.intValue();
        }
        final String s = claim instanceof jakarta.json.JsonString js ? js.getString() : claim.toString();
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** PK {@code admin_users.id} de l'agent backoffice courant. */
    public UUID getCurrentAdminId() {
        return adminUsers.findByKeycloakId(jwt.getSubject())
                .map(AdminUser::getId)
                .orElseThrow(() -> new ForbiddenException("Aucun agent backoffice pour ce compte Keycloak"));
    }

    public boolean hasRole(String role) {
        return identity.hasRole(role);
    }

    /** {@code sid} du JWT (identifiant de session Keycloak). */
    public String getKeycloakSessionId() {
        return claimString("sid");
    }

    /** {@code azp} du JWT (client émetteur : mobile-app / backoffice-app / quarkus-api). */
    public String getClientId() {
        return claimString("azp");
    }

    public AuditContext buildAuditContext() {
        return new AuditContext(
                jwt.getSubject(),
                getKeycloakSessionId(),
                getClientId(),
                getKycLevel(),
                resolveIp());
    }

    private String resolveIp() {
        if (routingContext == null || routingContext.request() == null) {
            return null;
        }
        final String forwarded = routingContext.request().getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return routingContext.request().remoteAddress() != null ? routingContext.request().remoteAddress().host() : null;
    }

    private String claimString(String name) {
        final Object value = jwt.getClaim(name);
        if (value == null) {
            return null;
        }
        return value instanceof jakarta.json.JsonString js ? js.getString() : value.toString();
    }
}
