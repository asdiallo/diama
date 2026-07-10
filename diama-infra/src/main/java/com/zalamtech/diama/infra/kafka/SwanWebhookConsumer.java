package com.zalamtech.diama.infra.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zalamtech.diama.domain.audit.NotificationService;
import com.zalamtech.diama.domain.transaction.Transaction;
import com.zalamtech.diama.domain.transaction.TransactionFSM;
import com.zalamtech.diama.domain.transaction.TransactionRepository;
import com.zalamtech.diama.domain.transaction.TransferService;
import com.zalamtech.diama.domain.transaction.TxStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.Map;

/**
 * Consomme les webhooks Swan enfilés par {@code SwanWebhookResource} (topic
 * {@code swan-webhooks}) : réception de fonds → bascule AML puis confirmation
 * Thunes ; rejet → transition {@code FAILED}.
 */
@Slf4j
@ApplicationScoped
public class SwanWebhookConsumer {

    @Inject
    TransactionRepository transactions;

    @Inject
    TransactionFSM fsm;

    @Inject
    TransferService transferService;

    @Inject
    NotificationService notifications;

    @Inject
    ObjectMapper objectMapper;

    @Incoming("swan-webhooks")
    @Transactional
    public void onMessage(String json) {
        final SwanWebhookEnvelope envelope = parse(json);
        if (envelope == null || envelope.transactionId() == null) {
            log.warn("Webhook Swan ignoré (transaction non appariée) : {}", json);
            return;
        }

        final Transaction tx = transactions.findByIdOptional(envelope.transactionId()).orElse(null);
        if (tx == null) {
            log.error("Transaction introuvable pour webhook Swan : {}", envelope.transactionId());
            return;
        }

        switch (envelope.eventType()) {
            case "TRANSACTION_BOOKED" -> handleBooked(tx, envelope);
            case "TRANSACTION_REJECTED" -> handleRejected(tx, envelope);
            default -> log.debug("Type d'événement Swan sans effet : {}", envelope.eventType());
        }
    }

    private void handleBooked(Transaction tx, SwanWebhookEnvelope envelope) {
        if (tx.getStatus() != TxStatus.INITIATED) {
            log.warn("Webhook Swan BOOKED ignoré (transaction {} déjà au statut {})", tx.getId(), tx.getStatus());
            return;
        }

        tx.setSwanTransferId(envelope.swanTransactionId());
        tx.setSwanAccountId(envelope.swanAccountId());

        // Pas de filtrage AML dédié à ce stade (hors périmètre de ce module) : la
        // réception effective des fonds Swan vaut levée de la vérification AML.
        fsm.transition(tx.getId(), TxStatus.AML_CLEARED, "Fonds Swan reçus", null);

        try {
            transferService.confirmAndSendToThunes(tx.getId());
        } catch (RuntimeException e) {
            log.error("Confirmation Thunes après réception Swan échouée : tx={}", tx.getId(), e);
        }
    }

    private void handleRejected(Transaction tx, SwanWebhookEnvelope envelope) {
        if (!TransactionFSM.isAllowed(tx.getStatus(), TxStatus.FAILED)) {
            log.warn("Webhook Swan REJECTED ignoré (transition FAILED interdite depuis {} pour tx={})",
                    tx.getStatus(), tx.getId());
            return;
        }

        final String reason = envelope.rejectedReason() != null ? envelope.rejectedReason() : "Virement Swan rejeté";
        fsm.transition(tx.getId(), TxStatus.FAILED, reason, null);

        // Rien à rembourser : le rejet Swan signifie que les fonds n'ont jamais été collectés.
        notifications.send(tx.getUserId(), tx.getId(), "TRANSFER_FAILED", Map.of("reason", reason));
    }

    private SwanWebhookEnvelope parse(String json) {
        try {
            return objectMapper.readValue(json, SwanWebhookEnvelope.class);
        } catch (Exception e) {
            log.error("Message swan-webhooks illisible", e);
            return null;
        }
    }
}
