package com.zalamtech.diama.common.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Corps de la demande d'initiation d'un transfert ({@code POST /api/v1/transfers}).
 *
 * <p>Le transfert s'appuie sur un devis préalablement émis et verrouillé en cache
 * Redis (15 min) ; seul son identifiant est transmis, garantissant que le taux et
 * les frais appliqués sont ceux présentés au client.</p>
 *
 * @param quotationId   devis verrouillé à consommer (clé de cache Redis)
 * @param beneficiaryId bénéficiaire destinataire (doit appartenir à l'utilisateur)
 */
public record CreateTransferRequest(

        @NotNull(message = "quotationId obligatoire")
        UUID quotationId,

        @NotNull(message = "beneficiaryId obligatoire")
        UUID beneficiaryId
) {
}
