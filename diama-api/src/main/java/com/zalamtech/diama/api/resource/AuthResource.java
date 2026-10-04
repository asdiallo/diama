package com.zalamtech.diama.api.resource;

import com.zalamtech.diama.common.dto.AccountRegistrationDto;
import com.zalamtech.diama.common.dto.RegistrationResultDto;
import com.zalamtech.diama.common.dto.ResendVerificationDto;
import com.zalamtech.diama.common.exception.RateLimitedException;
import com.zalamtech.diama.domain.support.RateLimiter;
import com.zalamtech.diama.domain.user.UserRegistrationService;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.Duration;

/**
 * Endpoints publics d'accès au compte (application mobile).
 *
 * <p>Seules routes de l'API utilisateur accessibles <b>sans JWT</b> : le client
 * {@code mobile-app} est public et ne peut pas créer de compte Keycloak
 * lui-même, c'est donc le backend qui le fait avec son compte de service.
 * La connexion reste assurée par Keycloak (Authorization Code + PKCE).</p>
 *
 * <p>Faute d'authentification, chaque route est plafonnée par adresse IP.
 * L'IP est lue de {@code X-Forwarded-For} : le plafond ne vaut que derrière un
 * proxy qui écrase cet en-tête.</p>
 */
@Path("/api/v1/auth")
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "auth")
public class AuthResource {

    private static final Duration WINDOW = Duration.ofHours(1);

    @Inject
    UserRegistrationService registration;

    @Inject
    RateLimiter rateLimiter;

    @Inject
    RoutingContext routingContext;

    @ConfigProperty(name = "diama.registration.rate-limit.register-per-hour", defaultValue = "10")
    int registerPerHour;

    @ConfigProperty(name = "diama.registration.rate-limit.resend-per-hour", defaultValue = "20")
    int resendPerHour;

    /** POST /register — création du compte (Keycloak + base) et envoi de l'email de confirmation. */
    @Operation(summary = "Inscription d'un nouvel utilisateur")
    @POST
    @Path("/register")
    public Response register(@Valid AccountRegistrationDto dto) {
        limit("register", registerPerHour);
        final RegistrationResultDto created = registration.register(dto);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    /**
     * POST /resend-verification — renvoi de l'email de confirmation.
     * Répond toujours 204, que l'adresse corresponde ou non à un compte en attente.
     */
    @Operation(summary = "Renvoi de l'email de confirmation")
    @POST
    @Path("/resend-verification")
    public Response resendVerification(@Valid ResendVerificationDto dto) {
        limit("resend", resendPerHour);
        registration.resendVerification(dto.email());
        return Response.noContent().build();
    }

    private void limit(String action, int maxPerHour) {
        if (!rateLimiter.tryAcquire(action + ":" + clientIp(), maxPerHour, WINDOW)) {
            throw new RateLimitedException();
        }
    }

    private String clientIp() {
        final String forwarded = routingContext.request().getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return routingContext.request().remoteAddress() != null
                ? routingContext.request().remoteAddress().host()
                : "unknown";
    }
}
