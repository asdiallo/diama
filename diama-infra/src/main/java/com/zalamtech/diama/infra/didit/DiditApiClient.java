package com.zalamtech.diama.infra.didit;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Client REST bas niveau de l'API Didit v3 (KYC / vérification d'identité).
 *
 * <p>Configuré via {@code quarkus.rest-client.didit-api.*} (application.yaml) ;
 * authentification par clé d'API ({@code x-api-key}, {@code diama.didit.api-key}).
 * Les corps de requête/réponse sont manipulés en {@code String} JSON brut, par
 * cohérence avec les autres clients partenaires ; {@code DiditClient} assure le
 * mapping vers/depuis les types du domaine. Les chemins conservent le slash
 * final attendu par Didit.</p>
 */
@RegisterRestClient(configKey = "didit-api")
@ClientHeaderParam(name = "x-api-key", value = "${diama.didit.api-key}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface DiditApiClient {

    @POST
    @Path("/v3/session/")
    String createSession(String body);

    @GET
    @Path("/v3/session/{id}/decision/")
    String getDecision(@PathParam("id") String sessionId);
}
