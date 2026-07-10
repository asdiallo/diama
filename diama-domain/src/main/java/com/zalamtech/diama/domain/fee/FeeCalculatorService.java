package com.zalamtech.diama.domain.fee;

import com.zalamtech.diama.common.exception.ResourceNotFoundException;
import com.zalamtech.diama.domain.exchange.ThunesPayer;
import com.zalamtech.diama.domain.exchange.ThunesPayerRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Calcul des frais de transfert à partir des paliers {@code fee_tiers}.
 *
 * <p>Règle : {@code frais = MAX(fixed_fee, percent_fee * montant)}, borné par
 * {@code min_fee} (plancher) et {@code max_fee} (plafond) lorsqu'ils sont définis.
 * Tous les montants sont arrondis à 2 décimales ({@link RoundingMode#HALF_UP}).</p>
 */
@ApplicationScoped
public class FeeCalculatorService {

    private static final int MONEY_SCALE = 2;

    @Inject
    FeeTierRepository feeTiers;

    @Inject
    ThunesPayerRepository payers;

    /**
     * Calcule les frais pour un montant dans la devise source, éventuellement
     * ciblé sur un payer (dont la devise de payout affine le choix du palier).
     *
     * @throws ResourceNotFoundException si aucun palier actif ne couvre le montant
     */
    public FeeResult calculateFee(String currency, BigDecimal amount, Long payerId) {
        final String destCurrency = payerId == null ? null
                : payers.findActiveById(payerId).map(ThunesPayer::getCurrency).orElse(null);

        final FeeTier tier = feeTiers.findApplicable(currency, destCurrency, amount, LocalDate.now())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun palier de frais pour %s montant=%s".formatted(currency, amount)));

        final BigDecimal fixed = nz(tier.getFixedFee());
        final BigDecimal percentComponent = nz(tier.getPercentFee()).multiply(amount);

        BigDecimal fee = fixed.max(percentComponent);
        if (tier.getMinFee() != null) {
            fee = fee.max(tier.getMinFee());   // plancher
        }
        if (tier.getMaxFee() != null) {
            fee = fee.min(tier.getMaxFee());   // plafond
        }

        return new FeeResult(
                scale(fixed),
                scale(percentComponent),
                scale(fee),
                tier.getId());
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
