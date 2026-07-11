package com.zalamtech.diama.infra.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zalamtech.diama.common.exception.InvalidTransferStateException;
import com.zalamtech.diama.common.exception.ResourceNotFoundException;
import com.zalamtech.diama.domain.audit.NotificationService;
import com.zalamtech.diama.domain.transaction.RFIService;
import com.zalamtech.diama.domain.transaction.SwanRefundService;
import com.zalamtech.diama.domain.transaction.ThunesTransferGateway;
import com.zalamtech.diama.domain.transaction.Transaction;
import com.zalamtech.diama.domain.transaction.TransactionFSM;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.Map;

/**
 * Consomme les callbacks Thunes enfilés par {@code ThunesWebhookResource} (topic
 * {@code thunes-callbacks}) : applique la transition FSM correspondante puis
 * déclenche les effets de bord propres à chaque statut.
 */
@Slf4j
@ApplicationScoped
public class ThunesCallbackConsumer {

    @Inject
    TransactionFSM fsm;

    @Inject
    RFIService rfiService;

    @Inject
    NotificationService notifications;

    @Inject
    SwanRefundService swanRefund;

    @Inject
    ThunesTransferGateway thunesGateway;

    @Inject
    ObjectMapper objectMapper;

    @Incoming("thunes-callbacks")
    @Transactional
    public void onMessage(String json) {
        final ThunesCallbackEvent event = parse(json);
        if (event == null || event.transactionId() == null || event.txStatus() == null) {
            log.warn("Callback Thunes ignoré (message illisible ou incomplet) : {}", json);
            return;
        }

        final Transaction tx;
        try {
            tx = fsm.transition(event.transactionId(), event.txStatus(), event.statusMessage(), null);
        } catch (ResourceNotFoundException e) {
            log.error("Transaction introuvable pour callback Thunes : {}", event.transactionId(), e);
            return;
        } catch (InvalidTransferStateException e) {
            // Rejeu / callback hors ordre (redélivrance Kafka, retry Thunes) : on n'échoue pas le message.
            log.warn("Callback Thunes ignoré (transition déjà appliquée ou hors ordre) : {}", e.getMessage());
            return;
        }

        switch (event.txStatus()) {
            case UNDER_REVIEW -> handleUnderReview(tx);
            case PROCESSING -> notifications.send(tx.getUserId(), tx.getId(), "TRANSFER_PROCESSING", Map.of());
            case DELIVERED -> handleDelivered(tx);
            case FAILED -> handleFailed(tx);
            default -> log.debug("Statut Thunes {} sans effet de bord additionnel (tx={})", event.txStatus(), tx.getId());
        }
    }

    private void handleUnderReview(Transaction tx) {
        thunesGateway.fetchOpenRFI(tx.getThunesTransactionId()).ifPresentOrElse(
                rfi -> rfiService.handleRFI(tx.getThunesExternalId(), rfi),
                () -> log.warn("Transaction {} passée UNDER_REVIEW sans RFI Thunes disponible", tx.getId()));
    }

    private void handleDelivered(Transaction tx) {
        // monthly_used est incrémenté par TransferEventConsumer (topic transfer-events),
        // déclenché par TransactionFSM.transition ci-dessus — pas ici, pour éviter un double comptage.
        notifications.send(tx.getUserId(), tx.getId(), "TRANSFER_DELIVERED",
                Map.of("amount", tx.getDestAmount() != null ? tx.getDestAmount() : tx.getSourceAmount(),
                        "currency", tx.getDestCurrency()));
    }

    private void handleFailed(Transaction tx) {
        notifications.send(tx.getUserId(), tx.getId(), "TRANSFER_FAILED",
                Map.of("reason", tx.getFailureReason() != null ? tx.getFailureReason() : ""));
        if (tx.getSwanTransferId() != null) {
            swanRefund.refundUser(tx);
        }
    }

    private ThunesCallbackEvent parse(String json) {
        try {
            return objectMapper.readValue(json, ThunesCallbackEvent.class);
        } catch (Exception e) {
            log.error("Message thunes-callbacks illisible", e);
            return null;
        }
    }
}
