package com.zalamtech.diama.domain.transaction;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository Panache des demandes d'information Thunes (PK UUID). */
@ApplicationScoped
public class RfiRequestRepository implements PanacheRepositoryBase<RfiRequest, UUID> {

    public Optional<RfiRequest> findByThunesRfiId(Long thunesRfiId) {
        return find("thunesRfiId", thunesRfiId).firstResultOptional();
    }

    /** RFI en attente de réponse, les plus anciennes d'abord (priorité ops). */
    public List<RfiRequest> findPending() {
        return find("status", Sort.by("createdAt").ascending(), RfiStatus.PENDING).list();
    }
}
