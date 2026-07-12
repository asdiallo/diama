package com.zalamtech.diama.mock;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

/**
 * Stubs WireMock de l'API Thunes v2 : catalogue de payers, devis,
 * confirmation de transaction et consultation. Les webhooks Thunes sont
 * entrants (endpoint Diama) : ils sont couverts par les tests de flux,
 * pas par un stub sortant.
 */
public final class ThunesClientMock {

    public static final long PAYER_ID = 4242L;
    public static final long QUOTATION_ID = 9101L;
    public static final long TRANSACTION_ID = 9102L;

    private ThunesClientMock() {
    }

    public static void install(WireMockServer server) {
        server.stubFor(get(urlPathEqualTo("/v2/money-transfer/payers"))
                .willReturn(okJson("""
                        [{"id": %d, "name": "Orange Money Guinée", "country_iso_code": "GN",
                          "currency": "GNF", "service": "MobileWallet",
                          "min_amount": 1.00, "max_amount": 5000.00, "precision": 2, "increment": 0.01}]
                        """.formatted(PAYER_ID))));

        server.stubFor(post(urlEqualTo("/v2/money-transfer/quotations"))
                .willReturn(okJson("""
                        {"id": %d, "payer_id": %d, "wholesale_fx_rate": 12000.0,
                         "expiration_date": "2099-01-01T00:00:00Z"}
                        """.formatted(QUOTATION_ID, PAYER_ID))));

        server.stubFor(post(urlPathMatching("/v2/money-transfer/quotations/[0-9]+/transactions"))
                .willReturn(okJson("""
                        {"id": %d, "status": "10000", "status_message": "CONFIRMED", "payer_id": %d}
                        """.formatted(TRANSACTION_ID, PAYER_ID))));

        server.stubFor(get(urlPathMatching("/v2/money-transfer/transactions/[0-9]+"))
                .willReturn(okJson("""
                        {"id": %d, "status": "40000", "status_message": "PROCESSING", "payer_id": %d}
                        """.formatted(TRANSACTION_ID, PAYER_ID))));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder okJson(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}
