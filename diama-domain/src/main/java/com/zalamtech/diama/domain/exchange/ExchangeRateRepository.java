package com.zalamtech.diama.domain.exchange;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository Panache des taux de change (PK UUID). */
@ApplicationScoped
public class ExchangeRateRepository implements PanacheRepositoryBase<ExchangeRate, UUID> {

    /** Tous les taux actifs, groupés par corridor (vue backoffice). */
    public List<ExchangeRate> findAllActive() {
        return list("isActive", Sort.by("sourceCurrency").and("destCurrency").and("fetchedAt", Sort.Direction.Descending), true);
    }

    /** Historique complet (actifs + inactifs) d'un corridor, le plus récent d'abord. */
    public List<ExchangeRate> findHistory(String sourceCurrency, String destCurrency, int page, int size) {
        return find("sourceCurrency = ?1 and destCurrency = ?2", Sort.by("fetchedAt").descending(), sourceCurrency, destCurrency)
                .page(Page.of(page, size))
                .list();
    }

    public long countHistory(String sourceCurrency, String destCurrency) {
        return count("sourceCurrency = ?1 and destCurrency = ?2", sourceCurrency, destCurrency);
    }

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
