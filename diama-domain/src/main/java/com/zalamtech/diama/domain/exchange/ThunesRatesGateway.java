package com.zalamtech.diama.domain.exchange;

import java.util.List;

/**
 * Port d'accès aux taux de change bruts de Thunes (fournisseur de payout).
 *
 * <p>Interface définie côté domaine (architecture hexagonale) ; l'adaptateur
 * REST concret vit dans {@code diama-infra} (module {@code thunes}). Permet au
 * moteur de taux de rester indépendant du transport HTTP et facilite les tests
 * (substitution par un double).</p>
 */
public interface ThunesRatesGateway {

    /** Récupère les taux actifs de tous les corridors/payers exposés par Thunes. */
    List<ThunesRateQuote> fetchActiveRates();
}
