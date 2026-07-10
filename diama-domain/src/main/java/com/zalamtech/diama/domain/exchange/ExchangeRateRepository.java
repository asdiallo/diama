package com.zalamtech.diama.domain.exchange;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Repository Panache des taux de change (PK UUID). */
@ApplicationScoped
public class ExchangeRateRepository implements PanacheRepositoryBase<ExchangeRate, UUID> {

    /**
     * Taux actif d'un corridor, effectif à la date donnée (le plus récent
     * dont {@code effective_date <= date}).
     */
    public Optional<ExchangeRate> findActiveByCorridorAndDate(String sourceCurrency,
                                                              String destCurrency,
                                                              LocalDate date) {
        return find("sourceCurrency = ?1 and destCurrency = ?2 and isActive = true "
                        + "and effectiveDate <= ?3",
                Sort.by("effectiveDate").descending(),
                sourceCurrency, destCurrency, date)
                .firstResultOptional();
    }

    /**
     * Taux actif d'un corridor pour un payer donné à la date donnée. Préfère le
     * taux spécifique au payer, à défaut le taux générique ({@code payer_id NULL}).
     */
    public Optional<ExchangeRate> findActiveByCorridorAndPayer(String sourceCurrency,
                                                               String destCurrency,
                                                               Long payerId,
                                                               LocalDate date) {
        return find("sourceCurrency = ?1 and destCurrency = ?2 "
                        + "and (thunesPayerId = ?3 or thunesPayerId is null) "
                        + "and isActive = true and effectiveDate <= ?4",
                Sort.by("thunesPayerId", Sort.Direction.Ascending, Sort.NullPrecedence.NULLS_LAST)
                        .and("effectiveDate", Sort.Direction.Descending),
                sourceCurrency, destCurrency, payerId, date)
                .firstResultOptional();
    }

    /** Taux exact d'un corridor/payer à une date d'effet précise (upsert du refresh). */
    public Optional<ExchangeRate> findByCorridorPayerAndEffectiveDate(String sourceCurrency,
                                                                      String destCurrency,
                                                                      Long payerId,
                                                                      LocalDate effectiveDate) {
        return find("sourceCurrency = ?1 and destCurrency = ?2 and thunesPayerId = ?3 "
                        + "and effectiveDate = ?4",
                sourceCurrency, destCurrency, payerId, effectiveDate)
                .firstResultOptional();
    }

    /** Désactive les taux actifs d'un corridor/payer (avant insertion d'un nouveau). */
    public long deactivateActive(String sourceCurrency, String destCurrency, Long payerId) {
        return update("isActive = false where sourceCurrency = ?1 and destCurrency = ?2 "
                        + "and thunesPayerId = ?3 and isActive = true",
                sourceCurrency, destCurrency, payerId);
    }
}
