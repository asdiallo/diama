package com.zalamtech.diama.common.dto;

/**
 * Contexte d'audit dérivé du JWT Keycloak, propagé aux événements
 * {@code audit_events} et aux services métier.
 *
 * @param keycloakUserId    {@code sub} du JWT (= users.keycloak_id / admin_users.keycloak_id)
 * @param keycloakSessionId {@code sid} du JWT
 * @param keycloakClient    {@code azp} du JWT ('mobile-app' | 'backoffice-app' | 'quarkus-api')
 * @param kycLevel          niveau KYC courant (claim custom), 0 si absent
 * @param ip                adresse IP de la requête d'origine, {@code null} hors contexte HTTP
 */
public record AuditContext(
        String keycloakUserId,
        String keycloakSessionId,
        String keycloakClient,
        int kycLevel,
        String ip
) {
}
