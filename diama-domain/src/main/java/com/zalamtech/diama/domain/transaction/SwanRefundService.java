package com.zalamtech.diama.domain.transaction;

import com.zalamtech.diama.common.exception.InvalidTransferStateException;
import com.zalamtech.diama.domain.audit.AuditService;
import com.zalamtech.diama.domain.audit.NotificationService;
import com.zalamtech.diama.domain.user.User;
import com.zalamtech.diama.domain.user.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Remboursement Swan d'un transfert en échec : virement retour vers l'IBAN de
 * l'expéditeur des fonds déjà collectés en EUR.
 */
@Slf4j
@ApplicationScoped
public class SwanRefundService {

    @Inject
    UserRepository users;

    @Inject
    SwanGateway swanGateway;

    @Inject
    TransactionFSM fsm;

    @Inject
    NotificationService notifications;

    @Inject
    AuditService audit;

    /** Rembourse l'expéditeur d'une transaction {@code FAILED} dont les fonds ont été collectés. */
    @Transactional
    public void refundUser(Transaction tx) {
        if (tx.getStatus() != TxStatus.FAILED) {
            throw new InvalidTransferStateException(
                    "Remboursement impossible : transaction %s au statut %s".formatted(tx.getId(), tx.getStatus()));
        }
        if (tx.getSwanTransferId() == null) {
            log.info("Aucun remboursement nécessaire (fonds jamais collectés) : tx={}", tx.getId());
            return;
        }

        final User sender = users.findByIdOptional(tx.getUserId())
                .orElseThrow(() -> new IllegalStateException("Expéditeur introuvable pour tx=" + tx.getId()));
        if (sender.getSwanIban() == null) {
            log.error("Remboursement Swan impossible : aucun IBAN pour l'utilisateur {} (tx={})",
                    sender.getId(), tx.getId());
            return;
        }

        final String refundRef = swanGateway.refund(
                tx.getSwanTransferId(),
                sender.getSwanIban(),
                sender.getFirstName() + " " + sender.getLastName(),
                tx.getSourceAmount(),
                tx.getSourceCurrency(),
                "Remboursement transfert " + tx.getId());

        tx.setRefundAmount(tx.getSourceAmount());
        tx.setRefundAt(OffsetDateTime.now(ZoneOffset.UTC));

        fsm.transition(tx.getId(), TxStatus.REFUNDED, "Remboursement Swan " + refundRef, null);

        audit.log(null, tx.getUserId(), null, "TRANSFER_REFUNDED", "TRANSACTION", tx.getId(), null,
                Map.of("swanRefundRef", refundRef, "amount", tx.getSourceAmount()));

        notifications.send(tx.getUserId(), tx.getId(), "TRANSFER_REFUNDED",
                Map.of("amount", tx.getSourceAmount(), "currency", tx.getSourceCurrency()));

        log.info("Remboursement Swan effectué : tx={} ref={}", tx.getId(), refundRef);
    }
}
