package com.zalamtech.diama.infra.thunes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Payload d'une RFI (Request For Information) Thunes : demande d'informations ou
 * de documents complémentaires (compliance) sur une transaction en cours.
 *
 * @param id             identifiant Thunes de la RFI
 * @param transactionId  transaction Thunes concernée
 * @param externalId     identifiant idempotent Diama (corrélation)
 * @param status         statut de la RFI (ex. {@code PENDING})
 * @param requestedInfo  informations demandées (structure libre, ex. {@code {"sender":["date_of_birth"]}})
 * @param requestedDocs  documents demandés (ex. {@code ["identification_documents"]})
 * @param description    description lisible fournie par Thunes
 * @param expirationDate échéance de réponse (ISO-8601)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ThunesRFIPayload(
        @JsonProperty("id") Long id,
        @JsonProperty("transaction_id") Long transactionId,
        @JsonProperty("external_id") String externalId,
        @JsonProperty("status") String status,
        @JsonProperty("requested_information") JsonNode requestedInfo,
        @JsonProperty("requested_documents") List<String> requestedDocs,
        @JsonProperty("description") String description,
        @JsonProperty("expiration_date") String expirationDate
) {
}
