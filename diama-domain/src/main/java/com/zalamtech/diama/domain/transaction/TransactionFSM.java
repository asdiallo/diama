package com.zalamtech.diama.domain.transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zalamtech.diama.common.exception.InvalidTransferStateException;
import com.zalamtech.diama.common.exception.ResourceNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.zalamtech.diama.domain.transaction.TxStatus.AML_CLEARED;
import static com.zalamtech.diama.domain.transaction.TxStatus.CANCELLED;
import static com.zalamtech.diama.domain.transaction.TxStatus.CONFIRMED;
import static com.zalamtech.diama.domain.transaction.TxStatus.DELIVERED;
import static com.zalamtech.diama.domain.transaction.TxStatus.FAILED;
import static com.zalamtech.diama.domain.transaction.TxStatus.INITIATED;
import static com.zalamtech.diama.domain.transaction.TxStatus.PROCESSING;
import static com.zalamtech.diama.domain.transaction.TxStatus.REFUNDED;
import static com.zalamtech.diama.domain.transaction.TxStatus.UNDER_REVIEW;

/**
 * Machine à états des transactions de transfert. Garantit que seules les
 * transitions autorisées sont appliquées, trace chaque changement dans
 * {@code transaction_status_history} et enfile un événement d'outbox
 * ({@code transfer-status-changed}) — le tout dans une unique transaction.
 *
 * <p>Transitions autorisées :</p>
 * <pre>
 *   INITIATED    → AML_CLEARED, FAILED, CANCELLED
 *   AML_CLEARED  → CONFIRMED, FAILED
 *   CONFIRMED    → UNDER_REVIEW, PROCESSING
 *   UNDER_REVIEW → PROCESSING, FAILED, CANCELLED
 *   PROCESSING   → DELIVERED, FAILED
 *   DELIVERED    → (terminal)
 *   FAILED       → REFUNDED
 *   REFUNDED / CANCELLED → (terminaux)
 * </pre>
 */
@Slf4j
@ApplicationScoped
public class TransactionFSM {

    private static final String EVENT_STATUS_CHANGED = "transfer-status-changed";

    /** Table des transitions autorisées (état courant → états cibles admis). */
    private static final Map<TxStatus, Set<TxStatus>> ALLOWED = new EnumMap<>(TxStatus.class);

    static {
        ALLOWED.put(INITIATED, Set.of(AML_CLEARED, FAILED, CANCELLED));
        ALLOWED.put(AML_CLEARED, Set.of(CONFIRMED, FAILED));
        ALLOWED.put(CONFIRMED, Set.of(UNDER_REVIEW, PROCESSING));
        ALLOWED.put(UNDER_REVIEW, Set.of(PROCESSING, FAILED, CANCELLED));
        ALLOWED.put(PROCESSING, Set.of(DELIVERED, FAILED));
        ALLOWED.put(DELIVERED, Set.of());
        ALLOWED.put(FAILED, Set.of(REFUNDED));
        ALLOWED.put(REFUNDED, Set.of());
        ALLOWED.put(CANCELLED, Set.of());
    }

    @Inject
    TransactionRepository transactions;

    @Inject
    TransferEventPublisher events;

    @Inject
    ObjectMapper objectMapper;

    /** {@code true} si {@code from → to} est une transition autorisée. */
    public static boolean isAllowed(TxStatus from, TxStatus to) {
        return from != null && to != null && ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /**
     * Applique une transition de statut.
     *
     * @param txId      transaction à faire transiter
     * @param newStatus statut cible
     * @param reason    motif métier (tracé dans l'historique)
     * @param adminId   agent backoffice à l'origine, {@code null} si transition système
     * @return la transaction mise à jour
     * @throws ResourceNotFoundException    transaction inexistante
     * @throws InvalidTransferStateException transition non autorisée
     */
    @Transactional
    public Transaction transition(UUID txId, TxStatus newStatus, String reason, UUID adminId) {
        final Transaction tx = transactions.findByIdOptional(txId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", txId));
        final TxStatus current = tx.getStatus();

        if (!isAllowed(current, newStatus)) {
            throw new InvalidTransferStateException(
                    "Transition interdite %s → %s (transaction %s)".formatted(current, newStatus, txId));
        }

        tx.setStatus(newStatus);
        if (newStatus == DELIVERED || newStatus == REFUNDED) {
            tx.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        }
        if (newStatus == FAILED && reason != null) {
            tx.setFailureReason(reason);
        }

        recordHistory(txId, current, newStatus, reason, adminId);
        events.publish(tx, EVENT_STATUS_CHANGED);

        log.info("Transition {} → {} (tx={}, admin={})", current, newStatus, txId, adminId);
        return tx;
    }

    /** Insère une ligne d'historique de statut (audit métier). */
    private void recordHistory(UUID txId, TxStatus from, TxStatus to, String reason, UUID adminId) {
        TransactionStatusHistory.builder()
                .transactionId(txId)
                .fromStatus(from)
                .toStatus(to)
                .triggeredBy(adminId != null ? "ADMIN:" + adminId : "SYSTEM")
                .adminId(adminId)
                .metadata(reasonJson(reason))
                .build()
                .persist();
    }

    private String reasonJson(String reason) {
        try {
            return objectMapper.writeValueAsString(Map.of("reason", reason != null ? reason : ""));
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
