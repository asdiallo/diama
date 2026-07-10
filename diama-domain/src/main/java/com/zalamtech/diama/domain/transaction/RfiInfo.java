package com.zalamtech.diama.domain.transaction;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Détail d'une RFI (Request For Information) Thunes, exprimé en langage du
 * domaine (aucune dépendance au DTO de transport {@code diama-infra}).
 *
 * @param thunesRfiId    identifiant Thunes de la RFI
 * @param requestedInfo  informations demandées, sérialisées en JSON (ex. {@code {"sender":["date_of_birth"]}})
 * @param requestedDocs  documents demandés (ex. {@code ["identification_documents"]})
 * @param description    description lisible fournie par Thunes
 * @param expiresAt      échéance de réponse
 */
public record RfiInfo(
        Long thunesRfiId,
        String requestedInfo,
        List<String> requestedDocs,
        String description,
        OffsetDateTime expiresAt
) {
}
