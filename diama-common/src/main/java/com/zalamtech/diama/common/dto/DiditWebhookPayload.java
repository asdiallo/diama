package com.zalamtech.diama.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Corps du webhook Didit v3 (événement {@code status.updated} principalement ;
 * les autres types — {@code data.updated}, {@code user.status.updated}, … —
 * sont reçus mais ignorés par {@code KycService.handleDiditWebhook}).
 *
 * @param sessionId   identifiant de la session de vérification Didit
 * @param status      statut de session : {@code Not Started} | {@code In Progress} |
 *                    {@code Approved} | {@code Declined} | {@code In Review} |
 *                    {@code Abandoned} | {@code Expired} | {@code Kyc Expired} |
 *                    {@code Resubmitted} | {@code Awaiting User} (sensible à la casse)
 * @param webhookType type d'événement, ex. {@code status.updated}
 * @param vendorData  identifiant Diama envoyé à la création de session (users.id)
 * @param workflowId  workflow Didit de la session (corrélé au niveau KYC visé)
 * @param timestamp   horodatage d'émission (epoch secondes)
 * @param decision    rapport de décision (présent sur les statuts finaux)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DiditWebhookPayload(
        @JsonProperty("session_id") String sessionId,
        String status,
        @JsonProperty("webhook_type") String webhookType,
        @JsonProperty("vendor_data") String vendorData,
        @JsonProperty("workflow_id") String workflowId,
        Long timestamp,
        Decision decision
) {

    /**
     * @param status          statut consolidé de la session
     * @param idVerifications vérifications documentaires exécutées par le workflow
     * @param reviews         revues manuelles côté Didit (commentaire du réviseur)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Decision(
            String status,
            @JsonProperty("id_verifications") List<IdVerification> idVerifications,
            List<Review> reviews
    ) {
    }

    /**
     * @param nodeId       identifiant du nœud de workflow, ex. {@code id_verification_1}
     * @param status       {@code Approved} | {@code Declined} | {@code In Review}
     * @param documentType type de document Didit, ex. {@code Passport}, {@code Identity Card}
     * @param issuingState pays émetteur (ISO 3166-1 alpha-3)
     * @param warnings     alertes relevées sur le document
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IdVerification(
            @JsonProperty("node_id") String nodeId,
            String status,
            @JsonProperty("document_type") String documentType,
            @JsonProperty("issuing_state") String issuingState,
            List<Warning> warnings
    ) {
    }

    /** @param risk code d'alerte Didit, ex. {@code DOCUMENT_EXPIRED} */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Warning(
            String risk,
            @JsonProperty("short_description") String shortDescription,
            @JsonProperty("long_description") String longDescription
    ) {
    }

    /** @param newStatus statut fixé par le réviseur, {@code comment} son commentaire libre */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Review(
            @JsonProperty("new_status") String newStatus,
            String comment
    ) {
    }
}
