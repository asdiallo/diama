package com.zalamtech.diama.domain.transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zalamtech.diama.common.exception.ResourceNotFoundException;
import com.zalamtech.diama.domain.audit.AuditService;
import com.zalamtech.diama.domain.audit.NotificationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gestion des RFI (Request For Information) Thunes : demandes d'informations ou
 * de documents complémentaires émises par la compliance Thunes sur une
 * transaction en cours de traitement.
 */
@Slf4j
@ApplicationScoped
public class RFIService {

    @Inject
    TransactionRepository transactions;

    @Inject
    RfiRequestRepository rfiRequests;

    @Inject
    TransactionFSM fsm;

    @Inject
    ThunesTransferGateway thunesGateway;

    @Inject
    NotificationService notifications;

    @Inject
    AuditService audit;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Traite l'ouverture d'une RFI : bascule la transaction en {@code UNDER_REVIEW}
     * (si la transition est encore possible), persiste la RFI de façon idempotente
     * et notifie l'utilisateur ainsi que le backoffice.
     *
     * @param externalId identifiant idempotent Diama envoyé à Thunes ({@code thunes_external_id})
     * @param rfi        détail de la RFI ouverte
     */
    @Transactional
    public void handleRFI(String externalId, RfiInfo rfi) {
        final Transaction tx = transactions.findByThunesExternalId(externalId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction introuvable pour externalId Thunes : " + externalId));

        if (TransactionFSM.isAllowed(tx.getStatus(), TxStatus.UNDER_REVIEW)) {
            fsm.transition(tx.getId(), TxStatus.UNDER_REVIEW, rfi.description(), null);
        }

        if (rfiRequests.findByThunesRfiId(rfi.thunesRfiId()).isPresent()) {
            log.debug("RFI Thunes déjà enregistrée : {} (tx={})", rfi.thunesRfiId(), tx.getId());
            return;
        }

        rfiRequests.persist(RfiRequest.builder()
                .transactionId(tx.getId())
                .thunesRfiId(rfi.thunesRfiId())
                .status(RfiStatus.PENDING)
                .requestedInfo(rfi.requestedInfo())
                .requestedDocs(toJsonArray(rfi.requestedDocs()))
                .thunesDescription(rfi.description())
                .expiresAt(rfi.expiresAt())
                .build());

        notifications.send(tx.getUserId(), tx.getId(), "RFI_DOCUMENT_REQUIRED",
                Map.of("thunesRfiId", rfi.thunesRfiId(), "description", rfi.description() != null ? rfi.description() : ""));

        audit.log(null, "RFI_PENDING_REVIEW", "TRANSACTION", tx.getId(), null,
                Map.of("thunesRfiId", rfi.thunesRfiId(), "expiresAt", String.valueOf(rfi.expiresAt())));

        log.info("RFI Thunes ouverte : {} (tx={})", rfi.thunesRfiId(), tx.getId());
    }

    /** Soumet la réponse du backoffice à une RFI (documents et/ou informations complémentaires). */
    @Transactional
    public void submitRFIResponse(UUID rfiId, List<RFIDocument> docs, Map<String, String> info) {
        final RfiRequest rfi = rfiRequests.findByIdOptional(rfiId)
                .orElseThrow(() -> new ResourceNotFoundException("RfiRequest", rfiId));

        for (RFIDocument doc : docs) {
            thunesGateway.uploadRFIDocument(rfi.getThunesRfiId(), doc.content(), doc.mimeType());
        }
        if (info != null && !info.isEmpty()) {
            thunesGateway.submitRFIInfo(rfi.getThunesRfiId(), info);
        }

        rfi.setStatus(RfiStatus.SUBMITTED);
        rfi.setSubmittedAt(OffsetDateTime.now(ZoneOffset.UTC));
        log.info("Réponse RFI Thunes soumise : {} ({} document(s))", rfi.getThunesRfiId(), docs.size());
    }

    /** RFI en attente de réponse, les plus anciennes d'abord (file de travail backoffice). */
    public List<RfiRequest> getRFIPendingForAdmin() {
        return rfiRequests.findPending();
    }

    private String toJsonArray(List<String> docs) {
        try {
            return objectMapper.writeValueAsString(docs != null ? docs : List.of());
        } catch (JsonProcessingException e) {
            log.warn("Sérialisation des documents RFI demandés impossible", e);
            return "[]";
        }
    }
}
