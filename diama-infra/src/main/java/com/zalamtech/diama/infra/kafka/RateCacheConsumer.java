package com.zalamtech.diama.infra.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

/**
 * Consomme le topic {@code rate-updated} : invalide le cache Redis du taux
 * concerné.
 *
 * <p>{@code ExchangeRateService} met déjà Redis à jour de façon synchrone au
 * moment du refresh ; ce consumer est un filet de sécurité (redélivrance,
 * écriture concurrente, future source de mise à jour hors service) — il
 * garantit qu'un lecteur ne verra jamais un taux périmé plus longtemps que le
 * décalage de traitement de l'outbox.</p>
 */
@Slf4j
@ApplicationScoped
public class RateCacheConsumer {

    @Inject
    RedisDataSource redis;

    @Inject
    ObjectMapper objectMapper;

    private KeyCommands<String> keys;

    @PostConstruct
    void init() {
        this.keys = redis.key(String.class);
    }

    @Incoming("rate-updated")
    public void onMessage(String json) {
        final RateUpdatedEvent event = parse(json);
        if (event == null) {
            return;
        }
        final String key = "rate:%s:%s:%s:%s".formatted(event.source(), event.dest(), event.payerId(), event.effectiveDate());
        keys.del(key);
        log.debug("Cache Redis invalidé : {}", key);
    }

    private RateUpdatedEvent parse(String json) {
        try {
            return objectMapper.readValue(json, RateUpdatedEvent.class);
        } catch (Exception e) {
            log.error("Message rate-updated illisible", e);
            return null;
        }
    }
}
