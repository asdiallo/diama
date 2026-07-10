package com.zalamtech.diama.infra.thunes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload de webhook Thunes notifiant un changement d'état de transaction
 * (topic interne {@code thunes-callbacks}). Reçu de façon asynchrone ;
 * corrélé à la transaction Diama via {@link #externalId()}.
 *
 * @param id            identifiant Thunes de la transaction concernée
 * @param externalId    identifiant idempotent Diama (corrélation)
 * @param status        nouveau statut Thunes
 * @param statusMessage détail du statut
 * @param payerId       payer Thunes
 * @param timestamp     horodatage de l'événement (ISO-8601)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ThunesCallbackPayload(
        @JsonProperty("id") Long id,
        @JsonProperty("external_id") String externalId,
        @JsonProperty("status") String status,
        @JsonProperty("status_message") String statusMessage,
        @JsonProperty("payer_id") Long payerId,
        @JsonProperty("timestamp") String timestamp
) {
}
