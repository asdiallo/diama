package com.zalamtech.diama.common.exception;

/** Plafond mensuel KYC dépassé pour le montant demandé (HTTP 422). */
public class LimitExceededException extends BusinessException {

    public LimitExceededException(String message) {
        super("MONTHLY_LIMIT_EXCEEDED", 422, message);
    }
}
