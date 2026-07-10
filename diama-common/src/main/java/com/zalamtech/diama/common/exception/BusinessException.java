package com.zalamtech.diama.common.exception;

/**
 * Erreur métier portant un code stable et un statut HTTP.
 *
 * <p>Les sous-classes concrètes fixent le {@code errorCode} (stable, exploitable
 * par le client mobile / backoffice) et le {@code httpStatus} correspondant.
 * Elles sont converties en réponse JSON par le mapper JAX-RS
 * {@code BusinessExceptionMapper} (module diama-api).</p>
 */
public abstract class BusinessException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;

    protected BusinessException(String errorCode, int httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
