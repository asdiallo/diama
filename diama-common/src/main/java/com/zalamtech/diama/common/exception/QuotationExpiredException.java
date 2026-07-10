package com.zalamtech.diama.common.exception;

/**
 * Devis introuvable ou expiré : le taux verrouillé (15 min en cache Redis) n'est
 * plus disponible, un nouveau devis doit être demandé — HTTP 410.
 */
public class QuotationExpiredException extends BusinessException {

    public QuotationExpiredException(String message) {
        super("QUOTATION_EXPIRED", 410, message);
    }
}
