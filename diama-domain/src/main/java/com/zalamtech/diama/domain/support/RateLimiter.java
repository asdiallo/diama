package com.zalamtech.diama.domain.support;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * Limiteur de débit à fenêtre fixe, adossé à Redis ({@code INCR} + {@code EXPIRE}).
 * Destiné aux endpoints publics (inscription, renvoi d'email), que rien d'autre
 * ne protège d'un appel en boucle.
 *
 * <p><b>Ouvert en cas de panne Redis</b> : mieux vaut laisser passer une
 * inscription que bloquer toutes les créations de compte.</p>
 */
@Slf4j
@ApplicationScoped
public class RateLimiter {

    private static final String KEY_PREFIX = "ratelimit:";

    private final ValueCommands<String, Long> counters;
    private final RedisDataSource redis;

    @Inject
    public RateLimiter(RedisDataSource redis) {
        this.redis = redis;
        this.counters = redis.value(Long.class);
    }

    /**
     * @return {@code true} si l'appel est autorisé, {@code false} si {@code max}
     *         appels ont déjà eu lieu sur la fenêtre courante pour cette clé
     */
    public boolean tryAcquire(String key, int max, Duration window) {
        final String redisKey = KEY_PREFIX + key;
        try {
            final long count = counters.incr(redisKey);
            if (count == 1L) {
                // Premier appel de la fenêtre : c'est lui qui fixe son expiration.
                redis.key().expire(redisKey, window);
            }
            return count <= max;
        } catch (RuntimeException e) {
            log.warn("Limiteur de débit indisponible, appel autorisé : {}", e.getMessage());
            return true;
        }
    }
}
