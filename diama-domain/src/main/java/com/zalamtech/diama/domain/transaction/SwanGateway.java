package com.zalamtech.diama.domain.transaction;

import java.math.BigDecimal;

/**
 * Port d'accès aux opérations de remboursement Swan.io (collecte EUR).
 *
 * <p>Interface définie côté domaine (architecture hexagonale) ; l'adaptateur REST
 * concret vit dans {@code diama-infra}.</p>
 *
 * @see ThunesTransferGateway
 */
public interface SwanGateway {

    /**
     * Émet un virement retour vers l'IBAN de l'expéditeur.
     *
     * @param originalSwanTransferId virement Swan initial ayant collecté les fonds
     * @param destinationIban        IBAN de l'expéditeur à rembourser
     * @param beneficiaryName        nom du titulaire de l'IBAN
     * @param amount                 montant à rembourser
     * @param currency               devise (ISO 4217, ex. {@code EUR})
     * @param reference               libellé du virement
     * @return référence Swan du virement de remboursement
     */
    String refund(String originalSwanTransferId, String destinationIban, String beneficiaryName,
                  BigDecimal amount, String currency, String reference);
}
