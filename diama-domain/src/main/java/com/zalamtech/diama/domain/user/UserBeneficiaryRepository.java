package com.zalamtech.diama.domain.user;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository Panache des bénéficiaires enregistrés (PK UUID). */
@ApplicationScoped
public class UserBeneficiaryRepository implements PanacheRepositoryBase<UserBeneficiary, UUID> {

    /** Bénéficiaires actifs de l'utilisateur, les plus récents d'abord. */
    public List<UserBeneficiary> findActiveByUserId(UUID userId) {
        return find("userId = ?1 and isActive = true", Sort.by("createdAt").descending(), userId).list();
    }

    /** Bénéficiaire par id restreint à son propriétaire (garde-fou d'appartenance). */
    public Optional<UserBeneficiary> findByIdAndUserId(UUID id, UUID userId) {
        return find("id = ?1 and userId = ?2", id, userId).firstResultOptional();
    }
}
