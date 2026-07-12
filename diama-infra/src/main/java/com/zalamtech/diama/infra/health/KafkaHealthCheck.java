package com.zalamtech.diama.infra.health;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Joignabilité du cluster Kafka + lag agrégé des consumer groups Diama
 * (retard entre l'offset commité et la fin de chaque partition).
 */
@Readiness
@ApplicationScoped
public class KafkaHealthCheck implements HealthCheck {

    private static final List<String> DIAMA_GROUPS = List.of(
            "diama-transfer-events", "diama-kyc-events", "diama-notifications",
            "diama-thunes-callbacks", "diama-rate-updated", "diama-audit-events",
            "diama-sumsub-webhooks", "diama-swan-webhooks");
    private static final long TIMEOUT_SECONDS = 2;

    @ConfigProperty(name = "kafka.bootstrap.servers", defaultValue = "localhost:9092")
    String bootstrapServers;

    private AdminClient adminClient;

    @PostConstruct
    void init() {
        final Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        this.adminClient = AdminClient.create(props);
    }

    @PreDestroy
    void close() {
        if (adminClient != null) {
            adminClient.close();
        }
    }

    @Override
    public HealthCheckResponse call() {
        final HealthCheckResponseBuilder builder = HealthCheckResponse.builder().name("kafka");
        try {
            final int nodes = adminClient.describeCluster().nodes()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS).size();
            builder.up().withData("nodes", nodes).withData("consumerGroupLag", totalLag());
            return builder.build();
        } catch (Exception e) {
            return builder.down().withData("error", String.valueOf(e.getMessage())).build();
        }
    }

    /** Lag total (toutes partitions confondues) des groupes Diama ; best-effort. */
    private long totalLag() {
        long lag = 0;
        for (String group : DIAMA_GROUPS) {
            try {
                final Map<TopicPartition, OffsetAndMetadata> committed = adminClient
                        .listConsumerGroupOffsets(group)
                        .partitionsToOffsetAndMetadata()
                        .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (committed.isEmpty()) {
                    continue;
                }
                final Map<TopicPartition, OffsetSpec> latestSpec = committed.keySet().stream()
                        .collect(Collectors.toMap(Function.identity(), tp -> OffsetSpec.latest()));
                final Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latest = adminClient
                        .listOffsets(latestSpec).all().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

                for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : committed.entrySet()) {
                    final ListOffsetsResult.ListOffsetsResultInfo end = latest.get(entry.getKey());
                    if (end != null && entry.getValue() != null) {
                        lag += Math.max(0, end.offset() - entry.getValue().offset());
                    }
                }
            } catch (Exception e) {
                // Groupe inexistant (aucun message consommé) ou timeout : ignoré.
            }
        }
        return lag;
    }
}
