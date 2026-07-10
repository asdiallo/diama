package com.zalamtech.diama.domain.user;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Synchronise l'utilisateur applicatif avec Keycloak (source de vérité de
 * l'identité). Crée l'utilisateur à la première connexion à partir des claims
 * JWT, puis maintient email / téléphone à jour.
 *
 * <p><b>Prérequis Keycloak</b> : le realm doit exposer les claims
 * {@code email} et {@code phone_number} (mappers), sans quoi la création
 * échouera (colonnes NOT NULL). {@code country_of_residence} est lu du claim
 * homonyme, à défaut {@code "FR"}.</p>
 */
@ApplicationScoped
public class UserSyncService {

    @Inject
    UserRepository users;

    @Transactional
    public User getOrCreateUser(JsonWebToken jwt) {
        final String keycloakId = jwt.getSubject();
        final String email = claim(jwt, "email");
        final String phone = claim(jwt, "phone_number");

        final User existing = users.findByKeycloakId(keycloakId).orElse(null);
        if (existing != null) {
            // Entité managée : les setters sont flushés en fin de transaction.
            if (email != null && !email.equals(existing.getEmail())) {
                existing.setEmail(email);
            }
            if (phone != null && !phone.equals(existing.getPhone())) {
                existing.setPhone(phone);
            }
            return existing;
        }

        final User created = User.builder()
                .keycloakId(keycloakId)
                .email(email)
                .phone(phone)
                .firstName(orDefault(claim(jwt, "given_name"), ""))
                .lastName(orDefault(claim(jwt, "family_name"), ""))
                .countryOfResidence(orDefault(claim(jwt, "country_of_residence"), "FR"))
                .build();
        users.persist(created);
        return created;
    }

    private static String claim(JsonWebToken jwt, String name) {
        final Object value = jwt.getClaim(name);
        if (value == null) {
            return null;
        }
        if (value instanceof jakarta.json.JsonString js) {
            return js.getString();
        }
        return value.toString();
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
