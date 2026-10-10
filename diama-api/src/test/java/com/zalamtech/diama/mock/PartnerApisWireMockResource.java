package com.zalamtech.diama.mock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.Map;

/**
 * Démarre un serveur WireMock unique portant les stubs des trois partenaires
 * (Thunes, Didit, Swan) et pointe les REST clients Quarkus dessus.
 *
 * <p>À utiliser avec {@code @QuarkusTestResource(value = ..., restrictToAnnotatedClass = true)}
 * pour ne pas imposer un redémarrage de l'application aux autres classes de test.</p>
 */
public class PartnerApisWireMockResource implements QuarkusTestResourceLifecycleManager {

    private WireMockServer server;

    @Override
    public Map<String, String> start() {
        server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        server.start();

        ThunesClientMock.install(server);
        DiditClientMock.install(server);
        SwanClientMock.install(server);

        return Map.of(
                "quarkus.rest-client.thunes-api.url", server.baseUrl(),
                "quarkus.rest-client.didit-api.url", server.baseUrl(),
                "quarkus.rest-client.swan-api.url", server.baseUrl());
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop();
        }
    }
}
