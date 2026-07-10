package com.zalamtech.diama.infra.swan;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Client REST bas niveau de l'API Swan.io (collecte EUR, IBAN dédiés).
 *
 * <p>Configuré via {@code quarkus.rest-client.swan-api.*} (application.yaml).
 * L'authentification est injectée par {@link SwanAuthFilter}.</p>
 */
@RegisterRestClient(configKey = "swan-api")
@RegisterProvider(SwanAuthFilter.class)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface SwanApiClient {

    @POST
    @Path("/v1/transfers/refunds")
    SwanRefundResponse refund(SwanRefundRequest request);
}
