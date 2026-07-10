package com.zalamtech.diama.domain.exchange;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

/** Repository Panache des payers Thunes (PK = id Thunes natif, {@code Long}). */
@ApplicationScoped
public class ThunesPayerRepository implements PanacheRepositoryBase<ThunesPayer, Long> {

    /** Payers actifs desservant un pays (ISO 3166-1 alpha-2). */
    public List<ThunesPayer> listActiveByCountry(String countryIso) {
        return list("countryIso = ?1 and isActive = true", Sort.by("name"), countryIso);
    }

    /** Tous les payers actifs (utilisé par le refresh des taux). */
    public List<ThunesPayer> listActive() {
        return list("isActive = true");
    }

    public Optional<ThunesPayer> findActiveById(Long id) {
        return find("id = ?1 and isActive = true", id).firstResultOptional();
    }
}
