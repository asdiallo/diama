package com.zalamtech.diama.mock;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

/**
 * Stubs WireMock de l'API Didit v3 : création de session et rapport de
 * décision (passeport approuvé). Le webhook {@code status.updated} est entrant
 * (endpoint Diama) : couvert par {@code KycFlowIT}.
 */
public final class DiditClientMock {

    public static final String SESSION_ID = "wm-session-1";
    public static final String SESSION_TOKEN = "wm-session-token";
    public static final String SESSION_URL = "https://verify.didit.me/en/session/wm-session-token";

    private DiditClientMock() {
    }

    public static void install(WireMockServer server) {
        server.stubFor(post(urlPathEqualTo("/v3/session/"))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"session_id": "%s", "session_token": "%s", "url": "%s",
                                 "status": "Not Started", "vendor_data": "wm-user-1"}
                                """.formatted(SESSION_ID, SESSION_TOKEN, SESSION_URL))));

        server.stubFor(get(urlPathMatching("/v3/session/.+/decision/"))
                .willReturn(okJson("""
                        {"session_id": "%s", "status": "Approved",
                         "id_verifications": [{"node_id": "id_verification_1", "status": "Approved",
                                               "document_type": "Passport", "issuing_state": "FRA"}]}
                        """.formatted(SESSION_ID))));
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder okJson(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }
}
