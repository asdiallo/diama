package com.zalamtech.diama.domain.transaction;

import com.zalamtech.diama.common.dto.AdminTransactionDto;
import com.zalamtech.diama.common.dto.RfiPendingDto;
import com.zalamtech.diama.common.dto.TransactionDetailDto;
import com.zalamtech.diama.common.dto.TransactionHistoryEntryDto;
import com.zalamtech.diama.common.exception.ResourceNotFoundException;
import com.zalamtech.diama.domain.audit.AuditService;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Consultation et actions manuelles backoffice sur les transactions. */
@Slf4j
@ApplicationScoped
public class AdminTransactionService {

    @Inject
    TransactionRepository transactions;

    @Inject
    TransactionStatusHistoryRepository history;

    @Inject
    TransactionFSM fsm;

    @Inject
    RFIService rfiService;

    @Inject
    AuditService audit;

    public List<AdminTransactionDto> search(UUID userId, TxStatus status, String sourceCurrency, String destCurrency,
                                            OffsetDateTime from, OffsetDateTime to, Sort sort, int page, int size) {
        return transactions.searchAdmin(userId, status, sourceCurrency, destCurrency, from, to, sort, page, size)
                .stream()
                .map(AdminTransactionMapper::toDto)
                .toList();
    }

    public long count(UUID userId, TxStatus status, String sourceCurrency, String destCurrency,
                      OffsetDateTime from, OffsetDateTime to) {
        return transactions.countAdmin(userId, status, sourceCurrency, destCurrency, from, to);
    }

    public TransactionDetailDto getDetail(UUID transactionId) {
        final Transaction tx = transactions.findByIdOptional(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId));
        final List<TransactionHistoryEntryDto> entries = history.findByTransactionId(transactionId).stream()
                .map(AdminTransactionService::toHistoryDto)
                .toList();
        return new TransactionDetailDto(AdminTransactionMapper.toDto(tx), entries);
    }

    /** Transition manuelle : le motif est obligatoire et tracé à la fois dans l'historique FSM et l'audit. */
    @Transactional
    public void forceStatus(UUID transactionId, TxStatus newStatus, String reason, UUID adminId) {
        final Transaction tx = transactions.findByIdOptional(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId));
        final TxStatus before = tx.getStatus();

        fsm.transition(transactionId, newStatus, reason, adminId);

        audit.log(null, tx.getUserId(), adminId, "TRANSACTION_FORCE_STATUS", "TRANSACTION", transactionId,
                Map.of("status", before.name()),
                Map.of("status", newStatus.name(), "reason", reason));

        log.info("Transition manuelle : tx={} {} → {} (admin={}, motif={})", transactionId, before, newStatus, adminId, reason);
    }

    /** RFI Thunes en attente de réponse, avec contexte transaction. */
    public List<RfiPendingDto> listPendingRfi() {
        return rfiService.getRFIPendingForAdmin().stream()
                .map(this::toPendingRfiDto)
                .toList();
    }

    /** Soumet la réponse backoffice à une RFI Thunes (documents et/ou informations). */
    @Transactional
    public void submitRfiDocs(UUID transactionId, UUID rfiId, List<RFIDocument> docs, Map<String, String> info, UUID adminId) {
        final Transaction tx = transactions.findByIdOptional(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId));

        rfiService.submitRFIResponse(rfiId, docs, info);

        audit.log(null, tx.getUserId(), adminId, "RFI_RESPONSE_SUBMITTED", "TRANSACTION", transactionId,
                null, Map.of("rfiId", rfiId, "documentCount", docs.size()));

        log.info("Réponse RFI soumise par le backoffice : tx={} rfi={} admin={}", transactionId, rfiId, adminId);
    }

    private RfiPendingDto toPendingRfiDto(RfiRequest rfi) {
        final Transaction tx = transactions.findByIdOptional(rfi.getTransactionId()).orElse(null);
        return new RfiPendingDto(
                rfi.getId(),
                rfi.getTransactionId(),
                tx != null ? tx.getUserId() : null,
                rfi.getThunesRfiId(),
                rfi.getThunesDescription(),
                rfi.getRequestedInfo(),
                rfi.getRequestedDocs(),
                tx != null ? tx.getSourceAmount() : null,
                tx != null ? tx.getSourceCurrency() : null,
                rfi.getExpiresAt(),
                rfi.getCreatedAt());
    }

    private static TransactionHistoryEntryDto toHistoryDto(TransactionStatusHistory h) {
        return new TransactionHistoryEntryDto(
                h.getFromStatus() != null ? h.getFromStatus().name() : null,
                h.getToStatus() != null ? h.getToStatus().name() : null,
                h.getTriggeredBy(),
                h.getAdminId(),
                h.getMetadata(),
                h.getChangedAt());
    }
}
