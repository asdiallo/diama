package com.zalamtech.diama.common.exception;

/** Opération refusée : la ressource n'appartient pas à l'utilisateur courant (HTTP 403). */
public class ForbiddenOperationException extends BusinessException {

    public ForbiddenOperationException(String message) {
        super("FORBIDDEN_OPERATION", 403, message);
    }
}
