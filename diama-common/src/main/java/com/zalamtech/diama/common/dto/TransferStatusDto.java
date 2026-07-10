package com.zalamtech.diama.common.dto;

import java.time.OffsetDateTime;

/**
 * Vue compacte de l'état d'un transfert (statut courant de la FSM).
 *
 * <p>Le {@code state} est le nom du {@code TxStatus} du domaine (le module
 * commun ne dépend pas du domaine) : {@code INITIATED}, {@code CONFIRMED},
 * {@code PROCESSING}, {@code DELIVERED}, {@code FAILED}, etc.</p>
 *
 * @param state         statut courant (nom du {@code TxStatus})
 * @param failureReason motif d'échec le cas échéant, sinon {@code null}
 * @param updatedAt     dernière mise à jour du statut
 * @param completedAt   date de finalisation (livraison / terminal), sinon {@code null}
 */
public record TransferStatusDto(
        String state,
        String failureReason,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt
) {
}
