package com.zalamtech.diama.domain.kyc;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository Panache des profils KYC (PK UUID). */
@ApplicationScoped
public class KycProfileRepository implements PanacheRepositoryBase<KycProfile, UUID> {

    public Optional<KycProfile> findByUserId(UUID userId) {
        return find("userId", userId).firstResultOptional();
    }

    public Optional<KycProfile> findByDiditSessionId(String diditSessionId) {
        return find("diditSessionId", diditSessionId).firstResultOptional();
    }

    /** Dossiers en attente de revue manuelle, les plus anciens d'abord (file ops). */
    public List<KycProfile> findPendingReview() {
        return find("verificationStatus", Sort.by("updatedAt").ascending(),
                KycVerificationStatus.PENDING).list();
    }

    /** Incrémente le montant consommé du plafond mensuel (transfert livré). */
    @Transactional
    public void incrementMonthlyUsed(UUID userId, BigDecimal amount) {
        update("monthlyUsed = monthlyUsed + ?1 where userId = ?2", amount, userId);
    }
}
