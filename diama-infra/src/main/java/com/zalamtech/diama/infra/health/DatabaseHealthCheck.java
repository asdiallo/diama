package com.zalamtech.diama.infra.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/** Ping PostgreSQL ({@code SELECT 1}) — base principale. */
@Readiness
@ApplicationScoped
public class DatabaseHealthCheck implements HealthCheck {

    @Inject
    DataSource dataSource;

    @Override
    public HealthCheckResponse call() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return HealthCheckResponse.up("postgresql");
        } catch (Exception e) {
            return HealthCheckResponse.builder()
                    .name("postgresql").down()
                    .withData("error", String.valueOf(e.getMessage()))
                    .build();
        }
    }
}
