package com.zalamtech.diama.mock;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

/**
 * Stubs WireMock de l'API Sumsub : création d'applicant, jeton SDK et statut
 * de revue (approbation GREEN). Le webhook {@code applicantReviewed} est
 * entrant (endpoint Diama) : couvert par {@code KycFlowIT}.
 */
public final class SumsubClientMock {

    public static final String APPLICANT_ID = "wm-applicant-1";
    public static final String SDK_TOKEN = "wm-sdk-token";

    private SumsubClientMock() {
    }

    public static void install(WireMockServer server) {
        server.stubFor(post(urlPathEqualTo("/resources/applicants"))
                .willReturn(okJson("""
                        {"id": "%s", "externalUserId": "wm-user-1"}
                        """.formatted(APPLICANT_ID))));

        server.stubFor(post(urlPathEqualTo("/resources/accessTokens"))
                .willReturn(okJson("""
                        {"token": "%s", "userId": "wm-user-1"}
                        """.formatted(SDK_TOKEN))));

        server.stubFor(get(urlPathMatching("/resources/applicants/.+/status"))
                .willReturn(okJson("""
                        {"reviewStatus": "completed",
                         "reviewResult": {"reviewAnswer": "GREEN", "rejectLabels": []}}
                        """)));

        server.stubFor(get(urlPathMatching("/resources/applicants/.+/documents"))
                .willReturn(okJson("""
                        {"items": [{"id": "wm-doc-1", "idDocType": "PASSPORT", "country": "FRA",
                                    "reviewResult": {"reviewAnswer": "GREEN"}, "status": "APPROVED"}]}
                        """)));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder okJson(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}
