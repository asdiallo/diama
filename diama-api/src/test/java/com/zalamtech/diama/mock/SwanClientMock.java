package com.zalamtech.diama.mock;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * Stubs WireMock de l'API Swan : jeton OAuth2 {@code client_credentials},
 * création de compte dédié et virement de remboursement.
 *
 * <p>Le client OIDC {@code swan} est désactivé dans le profil test
 * ({@code quarkus.oidc-client.swan.client-enabled=false}) : ces stubs ne sont
 * exercés que si un test le réactive en pointant {@code auth-server-url} vers
 * WireMock ({@code discovery-enabled=false}, {@code token-path=/oauth/token}).
 * Le webhook {@code TRANSACTION_BOOKED} est entrant : couvert par
 * {@code TransferFlowIT}.</p>
 */
public final class SwanClientMock {

    public static final String ACCOUNT_ID = "wm-swan-account-1";
    public static final String IBAN = "FR7612345000010000000000001";

    private SwanClientMock() {
    }

    public static void install(WireMockServer server) {
        server.stubFor(post(urlPathEqualTo("/oauth/token"))
                .willReturn(okJson("""
                        {"access_token": "wm-swan-token", "token_type": "Bearer", "expires_in": 3600}
                        """)));

        server.stubFor(post(urlPathEqualTo("/v1/accounts"))
                .willReturn(okJson("""
                        {"accountId": "%s", "iban": "%s", "bic": "SWNBFR22",
                         "holderName": "Test Holder", "status": "Enabled"}
                        """.formatted(ACCOUNT_ID, IBAN))));

        server.stubFor(post(urlPathEqualTo("/v1/transfers/refunds"))
                .willReturn(okJson("""
                        {"id": "wm-swan-refund-1", "status": "Booked"}
                        """)));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder okJson(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}
