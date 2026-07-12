package com.zalamtech.diama.infra.health;

import io.smallrye.health.api.Wellness;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Disponibilité du realm Keycloak ({@code /.well-known/openid-configuration}).
 * En {@code @Wellness} : l'API valide les JWT hors-ligne (clés en cache), une
 * indisponibilité Keycloak ne doit pas sortir les pods du load-balancing.
 */
@Wellness
@ApplicationScoped
public class KeycloakHealthCheck implements HealthCheck {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    @ConfigProperty(name = "quarkus.oidc.auth-server-url")
    Optional<String> authServerUrl;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    @Override
    public HealthCheckResponse call() {
        if (authServerUrl.isEmpty()) {
            return HealthCheckResponse.builder()
                    .name("keycloak").up()
                    .withData("status", "OIDC non configuré (profil test)")
                    .build();
        }
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(authServerUrl.get() + "/.well-known/openid-configuration"))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            final int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            return status == 200
                    ? HealthCheckResponse.up("keycloak")
                    : HealthCheckResponse.builder().name("keycloak").down().withData("httpStatus", status).build();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return HealthCheckResponse.builder()
                    .name("keycloak").down()
                    .withData("error", String.valueOf(e.getMessage()))
                    .build();
        }
    }
}
