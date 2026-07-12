package com.zalamtech.diama.infra.health;

import io.quarkus.redis.datasource.RedisDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/** Ping Redis — cache des taux et verrous de devis. */
@Readiness
@ApplicationScoped
public class RedisHealthCheck implements HealthCheck {

    @Inject
    RedisDataSource redis;

    @Override
    public HealthCheckResponse call() {
        try {
            final String pong = String.valueOf(redis.execute("PING"));
            return HealthCheckResponse.builder()
                    .name("redis").up()
                    .withData("response", pong)
                    .build();
        } catch (Exception e) {
            return HealthCheckResponse.builder()
                    .name("redis").down()
                    .withData("error", String.valueOf(e.getMessage()))
                    .build();
        }
    }
}
