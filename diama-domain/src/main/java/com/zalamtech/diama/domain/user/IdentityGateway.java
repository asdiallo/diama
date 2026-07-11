package com.zalamtech.diama.domain.user;

/**
 * Port de synchronisation vers l'IdP Keycloak (realm {@code transfer-app}).
 * Interface définie côté domaine (architecture hexagonale) ; l'adaptateur
 * concret ({@code KeycloakIdentityGateway}, appuyé sur l'Admin API Keycloak)
 * vit dans {@code diama-infra}.
 */
public interface IdentityGateway {

    /** Propage le niveau KYC dans le claim custom {@code kyc_level} de l'utilisateur Keycloak. */
    void updateKycLevel(String keycloakId, int level);

    /** Invalide une session Keycloak (déconnexion forcée, ex. action backoffice). */
    void invalidateSession(String keycloakSessionId);
}
