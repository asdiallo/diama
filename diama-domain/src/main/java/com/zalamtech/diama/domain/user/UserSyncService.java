package com.zalamtech.diama.domain.user;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.hibernate.exception.ConstraintViolationException;

/**
 * Synchronise l'utilisateur applicatif avec Keycloak (source de vérité de
 * l'identité). Crée l'utilisateur à la première connexion à partir des claims
 * JWT, puis maintient email / téléphone à jour.
 *
 * <p><b>Prérequis Keycloak</b> : le realm doit exposer les claims
 * {@code email} et {@code phone_number} (mappers), sans quoi la création
 * échouera (colonnes NOT NULL). {@code country_of_residence} est lu du claim
 * homonyme, à défaut {@code "FR"}.</p>
 *
 * <p><b>Concurrence</b> : après connexion, le client émet souvent plusieurs
 * requêtes en parallèle. Toutes appellent {@link #getOrCreateUser} avant que la
 * première n'ait committé, d'où des insertions concurrentes. La contrainte
 * unique sur {@code keycloak_id} arbitre : le perdant réessaie et retombe sur la
 * branche « utilisateur existant ».</p>
 */
@ApplicationScoped
public class UserSyncService {

    @Inject
    UserRepository users;

    /** Auto-injection : nécessaire pour que l'intercepteur {@code @Transactional} s'applique. */
    @Inject
    UserSyncService self;

    /**
     * Retourne l'utilisateur lié au JWT, en le créant à la première connexion.
     * Non transactionnel : orchestre une unique nouvelle tentative en cas de
     * course à la création (insertion concurrente sur {@code keycloak_id}).
     */
    public User getOrCreateUser(JsonWebToken jwt) {
        try {
            return self.syncInTx(jwt);
        } catch (PersistenceException e) {
            if (!isDuplicateKey(e)) {
                throw e;
            }
            // Course perdue : la ligne vient d'être committée par une autre
            // requête. Nouvelle tentative → branche « existant ».
            return self.syncInTx(jwt);
        }
    }

    @Transactional
    User syncInTx(JsonWebToken jwt) {
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
            // Un JWT valide prouve que le compte est en service : il sort
            // définitivement du périmètre de la purge des inscriptions abandonnées.
            if (Boolean.TRUE.equals(existing.getEmailVerificationPending())) {
                existing.setEmailVerificationPending(Boolean.FALSE);
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
        // Force le flush ici pour que toute violation de contrainte remonte
        // dans les limites de cette transaction (et soit rattrapable), plutôt
        // qu'au commit de l'appelant.
        users.flush();
        return created;
    }

    private static boolean isDuplicateKey(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof ConstraintViolationException) {
                return true;
            }
        }
        return false;
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
