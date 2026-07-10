package com.zalamtech.diama.common.dto;

/**
 * Requête de provisionnement d'un compte Swan dédié (collecte EUR).
 *
 * @param externalUserId référence Diama (users.id, en {@code String})
 * @param holderFirstName prénom du titulaire
 * @param holderLastName  nom du titulaire
 * @param holderEmail     e-mail du titulaire
 */
public record SwanAccountRequest(
        String externalUserId,
        String holderFirstName,
        String holderLastName,
        String holderEmail
) {
}
