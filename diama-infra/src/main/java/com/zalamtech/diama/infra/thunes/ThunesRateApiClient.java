package com.zalamtech.diama.infra.thunes;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

/**
 * Client REST Thunes v2 (taux de change). Configuré via
 * {@code quarkus.rest-client.thunes-api.url} (application.yaml).
 *
 * <p><b>Authentification</b> : l'API Thunes exige une signature HMAC par requête.
 * Elle sera ajoutée via un {@code ClientRequestFilter} dédié — hors périmètre de
 * ce module de calcul.</p>
 */
@RegisterRestClient(configKey = "thunes-api")
@Produces(MediaType.APPLICATION_JSON)
public interface ThunesRateApiClient {

    @GET
    @Path("/v2/money-transfer/rates")
    List<ThunesRateResponse> getRates();
}
