package com.zalamtech.diama.infra.swan;

import com.zalamtech.diama.common.dto.SwanAccount;
import com.zalamtech.diama.common.dto.SwanAccountRequest;
import com.zalamtech.diama.common.dto.SwanTransfer;
import com.zalamtech.diama.common.dto.SwanTransferRequest;
import io.quarkus.oidc.client.filter.OidcClientFilter;
import io.quarkus.oidc.client.reactive.filter.OidcClientRequestReactiveFilter;
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

import java.math.BigDecimal;
import java.util.List;

/**
 * Client REST bas niveau de l'API Swan.io (collecte EUR, IBAN dédiés).
 *
 * <p>Configuré via {@code quarkus.rest-client.swan-api.*} (application.yaml).
 * L'authentification OAuth2 {@code client_credentials} est gérée par
 * {@link OidcClientRequestReactiveFilter}, lié au client nommé {@code swan}
 * ({@code quarkus.oidc-client.swan.*}) : jeton acquis, mis en cache et
 * rafraîchi automatiquement par {@code quarkus-oidc-client-filter}.</p>
 *
 * <p>Simplifié en REST : l'API Swan réelle (comptes, virements) est en
 * pratique GraphQL — modélisée ici en REST par cohérence avec le reste de
 * l'intégration ({@code ThunesApiClient}, {@code DiditApiClient}), les
 * webhooks Swan restant eux authentiquement REST.</p>
 */
@RegisterRestClient(configKey = "swan-api")
@RegisterProvider(OidcClientRequestReactiveFilter.class)
@OidcClientFilter("swan")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface SwanApiClient {

    @POST
    @Path("/v1/accounts")
    SwanAccount createAccount(SwanAccountRequest request);

    @GET
    @Path("/v1/accounts/{id}/balance")
    BigDecimal getAccountBalance(@PathParam("id") String accountId);

    @POST
    @Path("/v1/transfers")
    SwanTransfer initiateTransfer(SwanTransferRequest request);

    @GET
    @Path("/v1/transactions/{id}")
    SwanTransfer getTransaction(@PathParam("id") String transactionId);

    @GET
    @Path("/v1/transactions")
    List<SwanTransfer> listTransactions(@QueryParam("since") String sinceIso);

    @POST
    @Path("/v1/transfers/refunds")
    SwanRefundResponse refund(SwanRefundRequest request);
}
