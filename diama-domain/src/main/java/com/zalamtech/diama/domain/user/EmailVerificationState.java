package com.zalamtech.diama.domain.user;

/** État de la confirmation d'email d'un compte dans l'IdP. */
public enum EmailVerificationState {
    VERIFIED,
    PENDING,
    /** Le compte n'existe plus dans l'IdP (supprimé hors de Diama). */
    MISSING
}
