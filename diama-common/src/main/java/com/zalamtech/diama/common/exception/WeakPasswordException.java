package com.zalamtech.diama.common.exception;

/** Mot de passe refusé par la politique du realm Keycloak (HTTP 422). */
public class WeakPasswordException extends BusinessException {
    public WeakPasswordException(String message) {
        super("WEAK_PASSWORD", 422, message);
    }
}
