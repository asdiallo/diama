package com.zalamtech.diama.domain.user;

/**
 * Port de synchronisation vers l'IdP Keycloak (realm {@code diama-transfer-app}).
 * Interface définie côté domaine (architecture hexagonale) ; l'adaptateur
 * concret ({@code KeycloakIdentityGateway}, appuyé sur l'Admin API Keycloak)
 * vit dans {@code diama-infra}.
 */
public interface IdentityGateway {

    /** Propage le niveau KYC dans le claim custom {@code kyc_level} de l'utilisateur Keycloak. */
    void updateKycLevel(String keycloakId, int level);

    /** Invalide une session Keycloak (déconnexion forcée, ex. action backoffice). */
    void invalidateSession(String keycloakSessionId);

    /**
     * Crée le compte dans l'IdP, <b>bloqué à la connexion tant que l'email n'est
     * pas confirmé</b>, et retourne son identifiant ({@code sub} des futurs JWT).
     *
     * @throws com.zalamtech.diama.common.exception.RegistrationConflictException email déjà utilisé
     * @throws com.zalamtech.diama.common.exception.WeakPasswordException mot de passe refusé par la politique du realm
     */
    String createUser(NewIdentity identity);

    /** Supprime un compte de l'IdP (compensation d'une inscription qui échoue côté base). */
    void deleteUser(String keycloakId);

    /**
     * Envoie (ou renvoie) l'email de confirmation d'adresse. Le lien mène à une
     * page de l'IdP, qui propose ensuite un retour vers l'application.
     */
    void sendVerificationEmail(String keycloakId);

    EmailVerificationState emailVerificationState(String keycloakId);
}
