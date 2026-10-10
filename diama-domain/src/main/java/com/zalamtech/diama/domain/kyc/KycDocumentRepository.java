package com.zalamtech.diama.domain.kyc;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository Panache des documents justificatifs KYC (PK UUID). */
@ApplicationScoped
public class KycDocumentRepository implements PanacheRepositoryBase<KycDocument, UUID> {

    public List<KycDocument> findByKycProfileId(UUID kycProfileId) {
        return find("kycProfileId", Sort.by("createdAt").descending(), kycProfileId).list();
    }

    public Optional<KycDocument> findByKycProfileIdAndDiditDocumentId(UUID kycProfileId, String diditDocumentId) {
        return find("kycProfileId = ?1 and diditDocumentId = ?2", kycProfileId, diditDocumentId).firstResultOptional();
    }
}
