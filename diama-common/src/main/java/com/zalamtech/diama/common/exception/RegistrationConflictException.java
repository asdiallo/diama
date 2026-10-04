package com.zalamtech.diama.common.exception;

/** Inscription refusée : l'email ou le téléphone est déjà rattaché à un compte (HTTP 409). */
public class RegistrationConflictException extends BusinessException {

    private RegistrationConflictException(String errorCode, String message) {
        super(errorCode, 409, message);
    }

    public static RegistrationConflictException emailTaken() {
        return new RegistrationConflictException("EMAIL_ALREADY_REGISTERED", "Un compte existe déjà avec cet email");
    }

    public static RegistrationConflictException phoneTaken() {
        return new RegistrationConflictException("PHONE_ALREADY_REGISTERED",
                "Un compte existe déjà avec ce numéro de téléphone");
    }
}
