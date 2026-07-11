package com.zalamtech.diama.domain.fee;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Repository Panache des paliers de frais (PK UUID). */
@ApplicationScoped
public class FeeTierRepository implements PanacheRepositoryBase<FeeTier, UUID> {

    /** Grille tarifaire active (vue backoffice), groupée par corridor / tranche. */
    public java.util.List<FeeTier> findAllActive() {
        return list("isActive",
                Sort.by("sourceCurrency").and("destCurrency").and("minAmount"),
                true);
    }

    /**
     * Palier actif correspondant au montant et au corridor à la date donnée.
     *
     * <p>Un palier spécifique au corridor ({@code dest_currency} renseignée) est
     * préféré au palier générique ({@code dest_currency NULL}) ; en cas d'égalité,
     * le palier de tranche la plus haute ({@code min_amount} le plus élevé) l'emporte.</p>
     */
    public Optional<FeeTier> findApplicable(String sourceCurrency, String destCurrency,
                                            BigDecimal amount, LocalDate date) {
        return find("sourceCurrency = ?1 and (destCurrency is null or destCurrency = ?2) "
                        + "and minAmount <= ?3 and (maxAmount is null or maxAmount >= ?3) "
                        + "and isActive = true "
                        + "and validFrom <= ?4 and (validUntil is null or validUntil >= ?4)",
                Sort.by("destCurrency", Sort.Direction.Ascending, Sort.NullPrecedence.NULLS_LAST)
                        .and("minAmount", Sort.Direction.Descending),
                sourceCurrency, destCurrency, amount, date)
                .firstResultOptional();
    }
}
