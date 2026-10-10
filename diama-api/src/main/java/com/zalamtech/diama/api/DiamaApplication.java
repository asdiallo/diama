package com.zalamtech.diama.api;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Descripteur OpenAPI global de l'API Diama : métadonnées, taxonomie des tags.
 * Le schéma de sécurité JWT ({@code bearerAuth}) est déclaré via configuration
 * ({@code quarkus.smallrye-openapi.security-scheme*}, application.yaml).
 */
@OpenAPIDefinition(
        info = @Info(
                title = "Diama API",
                version = "1.0.0",
                description = "Plateforme de transfert d'argent Europe ↔ Afrique de l'Ouest",
                contact = @Contact(name = "ZalamTech", email = "dev@zalamtech.com")),
        tags = {
                @Tag(name = "users", description = "Profil, bénéficiaires, appareils"),
                @Tag(name = "transfers", description = "Devis et transferts d'argent"),
                @Tag(name = "kyc", description = "Vérification d'identité (Didit)"),
                @Tag(name = "rates", description = "Taux de change et opérateurs de payout"),
                @Tag(name = "admin", description = "Backoffice (kyc-agent, ops-agent, admin)"),
                @Tag(name = "webhooks", description = "Callbacks partenaires (HMAC, sans Keycloak)")
        })
public class DiamaApplication extends Application {
}
