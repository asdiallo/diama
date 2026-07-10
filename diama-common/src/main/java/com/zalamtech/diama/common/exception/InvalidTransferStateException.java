package com.zalamtech.diama.common.exception;

/**
 * Transition de statut interdite par la FSM, ou opération incompatible avec
 * l'état courant du transfert (ex. annulation d'un transfert déjà confirmé) — HTTP 409.
 */
public class InvalidTransferStateException extends BusinessException {

    public InvalidTransferStateException(String message) {
        super("INVALID_TRANSFER_STATE", 409, message);
    }
}
