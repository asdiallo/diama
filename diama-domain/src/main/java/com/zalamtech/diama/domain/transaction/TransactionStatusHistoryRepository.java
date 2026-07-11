package com.zalamtech.diama.domain.transaction;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

/** Repository Panache de l'historique des transitions de statut (PK UUID). */
@ApplicationScoped
public class TransactionStatusHistoryRepository implements PanacheRepositoryBase<TransactionStatusHistory, UUID> {

    public List<TransactionStatusHistory> findByTransactionId(UUID transactionId) {
        return find("transactionId", Sort.by("changedAt").ascending(), transactionId).list();
    }
}
