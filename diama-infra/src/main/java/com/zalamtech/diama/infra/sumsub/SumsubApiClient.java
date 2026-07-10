package com.zalamtech.diama.infra.sumsub;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Client REST bas niveau de l'API Sumsub (KYC / vérification d'identité).
 *
 * <p>Configuré via {@code quarkus.rest-client.sumsub-api.*} (application.yaml).
 * L'authentification HMAC est injectée par {@link SumsubAuthFilter}. Les corps
 * de requête/réponse sont manipulés en {@code String} JSON brut (voir
 * {@link SumsubAuthFilter} pour la raison) ; {@code SumsubClient} assure le
 * mapping vers/depuis les types du domaine.</p>
 */
@RegisterRestClient(configKey = "sumsub-api")
@RegisterProvider(SumsubAuthFilter.class)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface SumsubApiClient {

    @POST
    @Path("/resources/applicants")
    String createApplicant(@QueryParam("levelName") String levelName, String body);

    @POST
    @Path("/resources/accessTokens")
    String generateAccessToken(@QueryParam("userId") String userId,
                               @QueryParam("levelName") String levelName,
                               @QueryParam("ttlInSecs") long ttlInSecs);

    @GET
    @Path("/resources/applicants/{id}/status")
    String getApplicantStatus(@PathParam("id") String applicantId);

    @GET
    @Path("/resources/applicants/{id}/documents")
    String getApplicantDocuments(@PathParam("id") String applicantId);
}
