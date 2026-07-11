package com.zalamtech.diama.domain.audit;

import com.zalamtech.diama.common.dto.AuditContext;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Producteur CDI de l'{@link AuditContext} de la requête courante, dérivé
 * directement du JWT Keycloak.
 *
 * <p>Le contexte est produit en portée {@link Dependent} : les services
 * {@code @ApplicationScoped} l'injectent via {@code Instance<AuditContext>} et
 * appellent {@code get()} à chaque opération pour obtenir un instantané frais du
 * JWT (résolu par requête). Hors contexte requête (jobs, consumers), aucun JWT
 * n'est disponible et {@code get()} lèvera une exception : l'audit est alors à la
 * charge de l'appelant.</p>
 */
@Dependent
public class AuditContextProducer {

    @Inject
    JsonWebToken jwt;

    @Inject
    Instance<RoutingContext> routingContext;

    @Produces
    @Dependent
    public AuditContext currentAuditContext() {
        return new AuditContext(
                jwt.getSubject(),
                claim("sid"),
                claim("azp"),
                kycLevel(),
                resolveIp());
    }

    private String claim(String name) {
        final Object value = jwt.getClaim(name);
        if (value == null) {
            return null;
        }
        return value instanceof jakarta.json.JsonString js ? js.getString() : value.toString();
    }

    private int kycLevel() {
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

    private String resolveIp() {
        if (!routingContext.isResolvable() || routingContext.get() == null || routingContext.get().request() == null) {
            return null;
        }
        final var request = routingContext.get().request();
        final String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.remoteAddress() != null ? request.remoteAddress().host() : null;
    }
}
