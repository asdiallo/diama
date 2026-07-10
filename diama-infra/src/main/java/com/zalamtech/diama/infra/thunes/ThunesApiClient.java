package com.zalamtech.diama.infra.thunes;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;
import java.util.Map;

/**
 * Client REST bas niveau de l'API Thunes v2 (transferts d'argent).
 *
 * <p>Configuré via {@code quarkus.rest-client.thunes-api.*} (application.yaml) :
 * URL de base ({@code THUNES_URL}, alignée sur {@code diama.thunes.base-url}) et
 * timeouts (connect 5 s / read 30 s). L'authentification Basic est injectée par
 * {@link ThunesBasicAuthFilter}.</p>
 *
 * <p>Cette interface expose le transport brut (DTO Thunes) ; l'orchestration et
 * la traduction vers le domaine sont assurées par {@link ThunesClient}.</p>
 */
@RegisterRestClient(configKey = "thunes-api")
@RegisterProvider(ThunesBasicAuthFilter.class)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ThunesApiClient {

    /** Liste les payers ; {@code countryIso} nul → tous les pays. */
    @GET
    @Path("/v2/money-transfer/payers")
    List<ThunesPayerResponse> getPayers(@QueryParam("country_iso_code") String countryIso);

    @POST
    @Path("/v2/money-transfer/quotations")
    ThunesQuotation createQuotation(ThunesQuotationRequest request);

    @POST
    @Path("/v2/money-transfer/quotations/{id}/transactions")
    ThunesTransaction confirmTransaction(@PathParam("id") Long quotationId, ThunesTransactionRequest request);

    @GET
    @Path("/v2/money-transfer/transactions/{id}")
    ThunesTransaction getTransaction(@PathParam("id") Long id);

    /**
     * Téléverse un document de RFI. Simplifié en corps binaire + type MIME porté
     * par l'en-tête {@code Content-Type} (l'API Thunes attend en réalité un
     * {@code multipart/form-data} ; à ajuster lors de l'intégration réelle).
     */
    @POST
    @Path("/v2/money-transfer/rfis/{id}/documents")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    void uploadRfiDocument(@PathParam("id") Long rfiId,
                           @HeaderParam("X-Document-Content-Type") String mimeType,
                           byte[] content);

    @POST
    @Path("/v2/money-transfer/rfis/{id}/information")
    void submitRfiInfo(@PathParam("id") Long rfiId, Map<String, String> info);

    /** RFI ouvertes pour une transaction Thunes donnée. */
    @GET
    @Path("/v2/money-transfer/transactions/{id}/rfis")
    List<ThunesRFIPayload> getRfis(@PathParam("id") Long transactionId);
}
