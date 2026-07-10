package com.zalamtech.diama.domain.transaction;

import com.zalamtech.diama.common.dto.SwanAccount;
import com.zalamtech.diama.common.dto.SwanAccountRequest;
import com.zalamtech.diama.common.dto.SwanTransfer;
import com.zalamtech.diama.common.dto.SwanTransferRequest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Port d'accès aux opérations Swan.io (collecte EUR, IBAN dédiés).
 *
 * <p>Interface définie côté domaine (architecture hexagonale) ; l'adaptateur REST
 * concret ({@code SwanClient}) vit dans {@code diama-infra}. Regroupe, à
 * l'instar de {@link ThunesTransferGateway}, l'ensemble des opérations Swan
 * plutôt qu'un port par cas d'usage.</p>
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

    /** Crée un compte Swan dédié pour un utilisateur (IBAN individuel). */
    SwanAccount createAccount(SwanAccountRequest request);

    /** Solde courant d'un compte Swan. */
    BigDecimal getAccountBalance(String accountId);

    /** Initie un virement sortant Swan. */
    SwanTransfer initiateTransfer(SwanTransferRequest request);

    /** Détail d'une transaction Swan (virement sortant ou entrant). */
    SwanTransfer getTransaction(String transactionId);

    /** Transactions Swan (tous comptes) survenues depuis {@code since} — utilisé pour la réconciliation. */
    List<SwanTransfer> listRecentTransactions(OffsetDateTime since);
}
