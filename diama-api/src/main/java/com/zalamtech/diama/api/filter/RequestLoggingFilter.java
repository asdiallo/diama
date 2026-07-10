package com.zalamtech.diama.api.filter;

import com.zalamtech.diama.api.security.SecurityContext;
import com.zalamtech.diama.domain.user.SessionClient;
import com.zalamtech.diama.domain.user.User;
import com.zalamtech.diama.domain.user.UserSessionService;
import com.zalamtech.diama.domain.user.UserSyncService;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * Journalise chaque requête authentifiée (utilisateur, session, IP) et
 * enregistre la session Keycloak dans {@code user_sessions} à sa première
 * requête. Best-effort : une erreur d'enregistrement n'échoue jamais la requête.
 *
 * <p>En production (auth proactive), le {@link JsonWebToken} est résolu avant
 * les filtres REST. La persistance de session est de toute façon best-effort.</p>
 */
@Slf4j
@ApplicationScoped
public class RequestLoggingFilter {

    @Inject
    JsonWebToken jwt;

    @Inject
    SecurityContext security;

    @Inject
    UserSyncService userSync;

    @Inject
    UserSessionService userSessions;

    @Inject
    RoutingContext routingContext;

    // Les endpoints renvoient des POJO (mode bloquant) : le filtre s'exécute
    // sur un thread worker, l'accès JDBC y est donc autorisé.
    @ServerRequestFilter
    public void filter(ContainerRequestContext ctx) {
        final String keycloakUserId = jwt.getSubject();
        if (keycloakUserId == null) {
            return; // requête anonyme : rien à tracer
        }

        final String sessionId = security.getKeycloakSessionId();
        final String ip = clientIp(ctx);
        log.info("{} {} — user={} sid={} ip={}",
                ctx.getMethod(), ctx.getUriInfo().getPath(), keycloakUserId, sessionId, ip);

        final SessionClient client = mapClient(security.getClientId());
        if (client == null || sessionId == null) {
            return; // token service (quarkus-api) ou sans session : pas de user_session
        }

        try {
            final User user = userSync.getOrCreateUser(jwt);
            userSessions.recordIfAbsent(user.getId(), sessionId, client, ip,
                    ctx.getHeaderString("User-Agent"));
        } catch (RuntimeException e) {
            log.warn("Enregistrement de session impossible pour sid={}", sessionId, e);
        }
    }

    private String clientIp(ContainerRequestContext ctx) {
        final String forwarded = ctx.getHeaderString("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        if (routingContext != null && routingContext.request().remoteAddress() != null) {
            return routingContext.request().remoteAddress().host();
        }
        return null;
    }

    private static SessionClient mapClient(String azp) {
        if (azp == null) {
            return null;
        }
        return switch (azp) {
            case "mobile-app" -> SessionClient.MOBILE_APP;
            case "backoffice-app" -> SessionClient.BACKOFFICE_APP;
            default -> null; // quarkus-api (client_credentials) ou inconnu
        };
    }
}
