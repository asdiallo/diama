package com.zalamtech.diama.domain.transaction;

import com.zalamtech.diama.domain.exchange.ThunesPayer;
import com.zalamtech.diama.domain.user.User;
import com.zalamtech.diama.domain.user.UserBeneficiary;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Port d'accès aux opérations de transfert de Thunes (payout Afrique).
 *
 * <p>Interface définie côté domaine (architecture hexagonale) ; l'adaptateur REST
 * concret ({@code ThunesClient}) vit dans {@code diama-infra}. Le domaine parle
 * ses propres types ({@link Transaction}, {@link User}, {@link UserBeneficiary},
 * {@link ThunesPayer}, {@link ThunesTransferResult}) — la construction des corps
 * de requête Thunes est entièrement encapsulée dans l'adaptateur.</p>
 *
 * @see com.zalamtech.diama.domain.exchange.ThunesRatesGateway
 */
public interface ThunesTransferGateway {

    /** Payers actifs desservant un pays (ISO 3166-1 alpha-2), sans persistance. */
    List<ThunesPayer> getPayers(String countryIso);

    /** Synchronise le catalogue complet des payers Thunes en base et le renvoie. */
    List<ThunesPayer> syncPayers();

    /**
     * Crée le devis Thunes puis confirme la transaction (payout).
     *
     * @param tx          transaction interne (montants, devises, payer, external id)
     * @param sender      émetteur (utilisateur) — identité requise par Thunes
     * @param beneficiary destinataire (coordonnées de crédit)
     * @return identifiants Thunes (devis + transaction) et statut
     */
    ThunesTransferResult confirmTransfer(Transaction tx, User sender, UserBeneficiary beneficiary);

    /** Récupère l'état courant d'une transaction Thunes. */
    ThunesTransferResult fetchTransaction(Long thunesTransactionId);

    /** Téléverse un document en réponse à une RFI (Request For Information) Thunes. */
    void uploadRFIDocument(Long thunesRfiId, byte[] content, String mimeType);

    /** Soumet les informations demandées par une RFI Thunes. */
    void submitRFIInfo(Long thunesRfiId, Map<String, String> info);

    /** RFI ouverte la plus récente d'une transaction Thunes, si applicable. */
    Optional<RfiInfo> fetchOpenRFI(Long thunesTransactionId);
}
