package com.zalamtech.diama.domain.transaction;

import com.zalamtech.diama.common.dto.OrphanedFund;
import com.zalamtech.diama.common.dto.SwanTransfer;
import com.zalamtech.diama.domain.audit.AuditService;
import com.zalamtech.diama.domain.user.User;
import com.zalamtech.diama.domain.user.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Réconciliation Swan ↔ PostgreSQL : filet de sécurité si un webhook
 * {@code TRANSACTION_BOOKED} a été manqué (fonds reçus mais jamais rapprochés
 * d'une transaction), déclenché par un scheduler horaire
 * ({@code FundsReconciliationScheduler}, module {@code diama-api}).
 */
@Slf4j
@ApplicationScoped
public class FundsVerificationService {

    /** Fenêtre de relevé Swan couverte à chaque passage — large marge par rapport à l'intervalle horaire du scheduler. */
    private static final Duration RECONCILE_WINDOW = Duration.ofHours(3);
    private static final String BOOKED_STATUS = "Booked";

    @Inject
    TransactionRepository transactions;

    @Inject
    UserRepository users;

    @Inject
    SwanGateway swanGateway;

    @Inject
    TransactionFSM fsm;

    @Inject
    TransferService transferService;

    @Inject
    AuditService audit;

    /** Passage de réconciliation complet : détecte les fonds orphelins et alerte le backoffice. */
    @Transactional
    public void reconcile() {
        final List<OrphanedFund> orphans = detectOrphanedFunds();
        if (!orphans.isEmpty()) {
            alertBackoffice(orphans);
        }
        log.info("Réconciliation Swan terminée : {} fonds orphelins détectés", orphans.size());
    }

    /**
     * Parcourt les transactions Swan récentes (tous comptes) : rattrape celles
     * correspondant à une transaction {@code INITIATED} jamais notifiée par
     * webhook (auto-guérison), et renvoie celles qui restent sans
     * correspondance.
     */
    @Transactional
    public List<OrphanedFund> detectOrphanedFunds() {
        final OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minus(RECONCILE_WINDOW);
        final List<SwanTransfer> recent = swanGateway.listRecentTransactions(since);

        final List<OrphanedFund> orphans = new ArrayList<>();
        for (SwanTransfer transfer : recent) {
            if (!BOOKED_STATUS.equalsIgnoreCase(transfer.status())) {
                continue; // seuls les crédits confirmés nous intéressent ici
            }
            if (transactions.findBySwanTransferId(transfer.transferId()).isPresent()) {
                continue; // déjà rapproché (webhook déjà traité)
            }

            final Optional<Transaction> matched = matchToInitiatedTransaction(transfer);
            if (matched.isPresent()) {
                selfHeal(matched.get(), transfer);
            } else {
                orphans.add(toOrphanedFund(transfer));
            }
        }
        return orphans;
    }

    /** Journalise une alerte backoffice par fonds orphelin (pas de canal de notification admin dédié). */
    @Transactional
    public void alertBackoffice(List<OrphanedFund> orphans) {
        for (OrphanedFund orphan : orphans) {
            audit.log(null, orphan.userId(), null, "SWAN_ORPHANED_FUND", "SWAN_ACCOUNT", null, null, orphan);
        }
        log.warn("{} fonds Swan orphelins détectés — revue backoffice nécessaire", orphans.size());
    }

    private Optional<Transaction> matchToInitiatedTransaction(SwanTransfer transfer) {
        return users.findBySwanAccountId(transfer.accountId())
                .flatMap(u -> transactions.findFirstInitiatedByUserAndAmount(u.getId(), transfer.amount()));
    }

    /** Rattrape un webhook Swan manqué : même traitement que {@code SwanWebhookConsumer#handleBooked}. */
    private void selfHeal(Transaction tx, SwanTransfer transfer) {
        tx.setSwanTransferId(transfer.transferId());
        tx.setSwanAccountId(transfer.accountId());
        fsm.transition(tx.getId(), TxStatus.AML_CLEARED, "Fonds Swan reçus (réconciliation)", null);

        try {
            transferService.confirmAndSendToThunes(tx.getId());
        } catch (RuntimeException e) {
            log.error("Confirmation Thunes après réconciliation Swan échouée : tx={}", tx.getId(), e);
        }

        log.warn("Webhook Swan manqué, rattrapé par réconciliation : tx={} swanTransferId={}", tx.getId(), transfer.transferId());
    }

    private OrphanedFund toOrphanedFund(SwanTransfer transfer) {
        final User user = users.findBySwanAccountId(transfer.accountId()).orElse(null);
        final UUID userId = user != null ? user.getId() : null;
        final String iban = user != null ? user.getSwanIban() : null;

        return new OrphanedFund(
                transfer.accountId(),
                iban,
                userId,
                transfer.amount(),
                transfer.currency(),
                transfer.transferId(),
                OffsetDateTime.now(ZoneOffset.UTC));
    }
}
