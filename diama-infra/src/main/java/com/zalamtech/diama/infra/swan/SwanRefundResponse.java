package com.zalamtech.diama.infra.swan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Réponse Swan à l'émission d'un virement retour.
 *
 * @param id     identifiant Swan du virement de remboursement
 * @param status statut du virement (ex. {@code Booked}, {@code PendingReview})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SwanRefundResponse(
        @JsonProperty("id") String id,
        @JsonProperty("status") String status
) {
}
