package com.zalamtech.diama.infra.health;

import com.zalamtech.diama.infra.thunes.ThunesApiClient;
import io.smallrye.health.api.Wellness;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Disponibilité de l'API Thunes ({@code GET /v2/money-transfer/payers}).
 * En {@code @Wellness} : un incident Thunes est visible sans sortir les
 * pods du load-balancing (les endpoints non-payout restent servables).
 */
@Wellness
@ApplicationScoped
public class ThunesHealthCheck implements HealthCheck {

    @Inject
    @RestClient
    ThunesApiClient thunes;

    @Override
    public HealthCheckResponse call() {
        try {
            final int payers = thunes.getPayers(null).size();
            return HealthCheckResponse.builder()
                    .name("thunes").up()
                    .withData("payers", payers)
                    .build();
        } catch (Exception e) {
            return HealthCheckResponse.builder()
                    .name("thunes").down()
                    .withData("error", String.valueOf(e.getMessage()))
                    .build();
        }
    }
}
