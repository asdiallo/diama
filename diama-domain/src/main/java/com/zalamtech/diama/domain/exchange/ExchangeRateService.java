package com.zalamtech.diama.domain.exchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zalamtech.diama.common.exception.ResourceNotFoundException;
import com.zalamtech.diama.domain.audit.KafkaOutbox;
import com.zalamtech.diama.domain.audit.KafkaOutboxRepository;
import com.zalamtech.diama.domain.audit.OutboxStatus;
import io.quarkus.cache.CacheResult;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Moteur de taux de change : lecture Redis-first avec repli PostgreSQL, et
 * rafraîchissement quotidien depuis Thunes.
 *
 * <p><b>Cache Redis</b> — clé {@code rate:{source}:{dest}:{payerId}:{date}},
 * TTL 24h. Le refresh quotidien pré-remplit la clé du jour ; le repli DB met en
 * cache le taux trouvé sous sa propre date d'effet.</p>
 *
 * <p><b>Événements</b> — chaque taux rafraîchi produit une entrée
 * {@code kafka_outbox} (topic {@code rate-updated}), publiée de façon asynchrone
 * par le poller d'outbox (pattern outbox, cohérent avec le reste du système).</p>
 */
@Slf4j
@ApplicationScoped
public class ExchangeRateService {

    static final String RATE_UPDATED_TOPIC = "rate-updated";
    private static final long RATE_TTL_SECONDS = Duration.ofHours(24).toSeconds();

    @Inject
    ExchangeRateRepository exchangeRates;

    @Inject
    ThunesPayerRepository payers;

    @Inject
    ThunesRatesGateway thunesRates;

    @Inject
    KafkaOutboxRepository outbox;

    @Inject
    RedisDataSource redis;

    @Inject
    ObjectMapper mapper;

    private ValueCommands<String, String> cache;

    @PostConstruct
    void init() {
        this.cache = redis.value(String.class);
    }

    // ---------------------------------------------------------------------
    //  Lecture du taux actif
    // ---------------------------------------------------------------------

    /**
     * Taux actif d'un corridor pour un payer : Redis en priorité, sinon PostgreSQL
     * (avec mise en cache du résultat).
     *
     * @throws ResourceNotFoundException si aucun taux actif n'existe pour le corridor
     */
    public ExchangeRate getActiveRate(String source, String dest, Long payerId) {
        final String key = redisKey(source, dest, payerId, LocalDate.now());

        final Optional<ExchangeRate> cached = getFromRedis(key);
        if (cached.isPresent()) {
            return cached.get();
        }

        final ExchangeRate rate = exchangeRates
                .findActiveByCorridorAndPayer(source, dest, payerId, LocalDate.now())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun taux actif pour le corridor %s→%s".formatted(source, dest)));

        storeInRedis(rate);
        return rate;
    }

    // ---------------------------------------------------------------------
    //  Cache Redis (impératif : clés et TTL explicites)
    // ---------------------------------------------------------------------

    /** Met le taux en cache Redis (clé dérivée du taux, TTL 24h). */
    public void storeInRedis(ExchangeRate rate) {
        final String key = redisKey(rate.getSourceCurrency(), rate.getDestCurrency(),
                rate.getThunesPayerId(), rate.getEffectiveDate());
        try {
            cache.setex(key, RATE_TTL_SECONDS, mapper.writeValueAsString(rate));
        } catch (JsonProcessingException e) {
            log.warn("Mise en cache Redis impossible pour {}", key, e);
        }
    }

    /** Lit un taux depuis Redis par sa clé complète. */
    public Optional<ExchangeRate> getFromRedis(String corridorKey) {
        final String json = cache.get(corridorKey);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(json, ExchangeRate.class));
        } catch (JsonProcessingException e) {
            log.warn("Lecture cache Redis illisible pour {} : {}", corridorKey, e.getMessage());
            return Optional.empty();
        }
    }

    private static String redisKey(String source, String dest, Long payerId, LocalDate date) {
        return "rate:%s:%s:%s:%s".formatted(source, dest, payerId, date);
    }

    // ---------------------------------------------------------------------
    //  Payers actifs (mis en cache via @CacheResult → Redis)
    // ---------------------------------------------------------------------

    /** Opérateurs de payout actifs d'un pays (résultat mis en cache Redis). */
    @CacheResult(cacheName = "active-payers-by-country")
    public List<ThunesPayer> getActivePayers(String countryIso) {
        return payers.listActiveByCountry(countryIso);
    }

    // ---------------------------------------------------------------------
    //  Rafraîchissement quotidien depuis Thunes
    // ---------------------------------------------------------------------

    /**
     * Récupère les taux Thunes, met à jour {@code exchange_rates} + Redis et
     * enfile un événement {@code rate-updated} par corridor. Appelé par le
     * scheduler quotidien.
     *
     * @return nombre de corridors rafraîchis
     */
    @Transactional
    public int refreshRatesFromThunes() {
        final List<ThunesRateQuote> quotes = thunesRates.fetchActiveRates();
        for (ThunesRateQuote quote : quotes) {
            upsertRate(quote);
        }
        log.info("Rafraîchissement des taux Thunes : {} corridors mis à jour", quotes.size());
        return quotes.size();
    }

    private void upsertRate(ThunesRateQuote quote) {
        final LocalDate today = LocalDate.now();

        ExchangeRate rate = exchangeRates.findByCorridorPayerAndEffectiveDate(
                quote.sourceCurrency(), quote.destCurrency(), quote.payerId(), today).orElse(null);

        if (rate != null) {
            rate.setRate(quote.rate());
            rate.setIsActive(Boolean.TRUE);
        } else {
            exchangeRates.deactivateActive(quote.sourceCurrency(), quote.destCurrency(), quote.payerId());
            rate = ExchangeRate.builder()
                    .sourceCurrency(quote.sourceCurrency())
                    .destCurrency(quote.destCurrency())
                    .thunesPayerId(quote.payerId())
                    .rate(quote.rate())
                    .effectiveDate(today)
                    .isActive(Boolean.TRUE)
                    .build();
            exchangeRates.persist(rate);
        }

        // Flush pour matérialiser la colonne générée client_rate avant cache/événement.
        exchangeRates.flush();
        storeInRedis(rate);
        enqueueRateUpdated(rate);
    }

    /** Package-privé : réutilisé par {@code AdminRateService} pour republier après une modification manuelle. */
    void enqueueRateUpdated(ExchangeRate rate) {
        final ObjectNode payload = mapper.createObjectNode();
        payload.put("source", rate.getSourceCurrency());
        payload.put("dest", rate.getDestCurrency());
        payload.put("payerId", rate.getThunesPayerId());
        payload.put("rate", rate.getRate());
        payload.put("clientRate", rate.getClientRate());
        payload.put("effectiveDate", rate.getEffectiveDate().toString());

        outbox.persist(KafkaOutbox.builder()
                .aggregateId(rate.getId())
                .aggregateType("RATE")
                .eventType("RATE_UPDATED")
                .payload(payload.toString())
                .kafkaTopic(RATE_UPDATED_TOPIC)
                .status(OutboxStatus.PENDING)
                .build());
    }
}
