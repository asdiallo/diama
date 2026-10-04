package com.zalamtech.diama.domain.user;

/**
 * Identité à créer dans l'IdP. L'email sert d'identifiant de connexion
 * ({@code registrationEmailAsUsername} sur le realm).
 */
public record NewIdentity(String email, String phone, String firstName, String lastName, String password) {

    /** Masque le mot de passe. */
    @Override
    public String toString() {
        return "NewIdentity[email=" + email + ", phone=" + phone + ", password=***]";
    }
}
