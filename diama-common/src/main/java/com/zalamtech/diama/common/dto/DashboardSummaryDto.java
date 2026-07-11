package com.zalamtech.diama.common.dto;

import java.math.BigDecimal;

/**
 * Synthèse du tableau de bord backoffice — fenêtre du jour courant (UTC) pour
 * les compteurs d'activité, instantané courant pour les files d'attente.
 *
 * @param txCount        transactions initiées aujourd'hui
 * @param txVolume       montant total envoyé aujourd'hui (devise source)
 * @param txSuccessRate  {@code DELIVERED / (DELIVERED + FAILED)} du jour, {@code 0} si aucune transaction conclue
 * @param pendingRFI     RFI Thunes en attente de réponse
 * @param pendingKYC     dossiers KYC en attente de revue
 * @param activeSessions sessions Keycloak actives
 * @param revenueToday   frais perçus aujourd'hui sur les transferts livrés (devise source)
 */
public record DashboardSummaryDto(
        long txCount,
        BigDecimal txVolume,
        double txSuccessRate,
        long pendingRFI,
        long pendingKYC,
        long activeSessions,
        BigDecimal revenueToday
) {
}
