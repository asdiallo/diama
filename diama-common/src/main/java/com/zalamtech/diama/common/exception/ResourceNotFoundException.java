package com.zalamtech.diama.common.exception;

import java.util.UUID;

/** Ressource inexistante ou non accessible par l'utilisateur courant (HTTP 404). */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String message) {
        super("RESOURCE_NOT_FOUND", 404, message);
    }

    public ResourceNotFoundException(String resource, UUID id) {
        super("RESOURCE_NOT_FOUND", 404, "%s introuvable : %s".formatted(resource, id));
    }
}
