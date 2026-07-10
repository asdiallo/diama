package com.zalamtech.diama.domain.quotation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zalamtech.diama.common.dto.LimitCheckResult;
import com.zalamtech.diama.common.dto.QuotationDto;
import com.zalamtech.diama.common.exception.LimitExceededException;
import com.zalamtech.diama.common.exception.ResourceNotFoundException;
import com.zalamtech.diama.domain.exchange.ExchangeRate;
import com.zalamtech.diama.domain.exchange.ExchangeRateService;
import com.zalamtech.diama.domain.exchange.ThunesPayer;
import com.zalamtech.diama.domain.exchange.ThunesPayerRepository;
import com.zalamtech.diama.domain.fee.FeeCalculatorService;
import com.zalamtech.diama.domain.fee.FeeResult;
import com.zalamtech.diama.domain.user.UserService;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Construit un devis de transfert : taux client, montant reçu, frais, total
 * débité et délai estimé. Vérifie le plafond KYC de l'utilisateur avant de
 * renvoyer le devis, puis verrouille le taux 15 min en cache Redis
 * (clé {@code quot:{userId}:{quotationId}}).
 */
@Slf4j
@ApplicationScoped
public class QuotationService {

    private static final int MONEY_SCALE = 2;
    private static final Duration QUOTATION_TTL = Duration.ofMinutes(15);

    @Inject
    ThunesPayerRepository payers;

    @Inject
    ExchangeRateService exchangeRates;

    @Inject
    FeeCalculatorService feeCalculator;

    @Inject
    UserService users;

    @Inject
    RedisDataSource redis;

    @Inject
    ObjectMapper mapper;

    private ValueCommands<String, String> cache;

    @PostConstruct
    void init() {
        this.cache = redis.value(String.class);
    }

    @Transactional
    public QuotationDto buildQuotation(UUID userId, BigDecimal sourceAmount,
                                       String sourceCurrency, Long payerId) {
        final ThunesPayer payer = payers.findActiveById(payerId)
                .orElseThrow(() -> new ResourceNotFoundException("Payer Thunes introuvable : " + payerId));
        final String destCurrency = payer.getCurrency();

        final ExchangeRate rate = exchangeRates.getActiveRate(sourceCurrency, destCurrency, payerId);
        final BigDecimal clientRate = effectiveClientRate(rate);
        final BigDecimal destAmount = sourceAmount.multiply(clientRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        final FeeResult fee = feeCalculator.calculateFee(sourceCurrency, sourceAmount, payerId);
        final BigDecimal totalDebit = sourceAmount.add(fee.totalFee()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        // Contrôle du plafond KYC (sur le montant envoyé) avant émission du devis.
        final LimitCheckResult limit = users.checkMonthlyLimit(userId, sourceAmount);
        if (!limit.allowed()) {
            throw new LimitExceededException(
                    "Plafond mensuel dépassé : disponible %s, demandé %s (KYC niveau %d)"
                            .formatted(limit.available(), sourceAmount, limit.kycLevel()));
        }

        final UUID quotationId = UUID.randomUUID();
        final QuotationDto quotation = new QuotationDto(
                quotationId,
                sourceAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                sourceCurrency,
                destAmount,
                destCurrency,
                rate.getRate(),
                clientRate,
                fee.totalFee(),
                totalDebit,
                payer.getName(),
                estimatedDelivery(payer.getServiceType()),
                OffsetDateTime.now(ZoneOffset.UTC).plus(QUOTATION_TTL));

        cacheQuotation(userId, quotation);
        return quotation;
    }

    /** Taux client : colonne générée si disponible, sinon recalcul {@code rate * (1 - spread)}. */
    private static BigDecimal effectiveClientRate(ExchangeRate rate) {
        if (rate.getClientRate() != null) {
            return rate.getClientRate();
        }
        return rate.getRate().multiply(BigDecimal.ONE.subtract(rate.getSpreadPct()));
    }

    private static String estimatedDelivery(String serviceType) {
        if (serviceType == null) {
            return "Variable";
        }
        return switch (serviceType) {
            case "MobileWallet" -> "Quelques minutes";
            case "CashPickup" -> "Sous 1 heure";
            case "BankAccount" -> "1 à 2 jours ouvrés";
            default -> "Variable";
        };
    }

    private void cacheQuotation(UUID userId, QuotationDto quotation) {
        final String key = "quot:%s:%s".formatted(userId, quotation.quotationId());
        try {
            cache.setex(key, QUOTATION_TTL.toSeconds(), mapper.writeValueAsString(quotation));
        } catch (JsonProcessingException e) {
            // Le verrouillage cache est best-effort : le devis reste valide en réponse.
            log.warn("Mise en cache du devis impossible pour {}", key, e);
        }
    }
}
