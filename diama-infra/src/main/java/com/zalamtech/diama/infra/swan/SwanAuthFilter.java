package com.zalamtech.diama.infra.swan;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import org.eclipse.microprofile.config.ConfigProvider;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Filtre d'authentification Swan.io, sur le même principe que
 * {@link com.zalamtech.diama.infra.thunes.ThunesBasicAuthFilter}.
 *
 * <p>Simplifié en Basic Auth (client-id / client-secret) pour ce module ; l'API
 * Swan réelle attend un jeton OAuth2 client_credentials (à échanger via
 * {@code quarkus-oidc-client} lors de l'intégration complète).</p>
 */
public class SwanAuthFilter implements ClientRequestFilter {

    private final String authorizationHeader;

    public SwanAuthFilter() {
        final var config = ConfigProvider.getConfig();
        final String clientId = config.getOptionalValue("diama.swan.client-id", String.class).orElse("");
        final String clientSecret = config.getOptionalValue("diama.swan.client-secret", String.class).orElse("");
        final String token = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        this.authorizationHeader = "Basic " + token;
    }

    @Override
    public void filter(ClientRequestContext requestContext) {
        requestContext.getHeaders().putSingle(HttpHeaders.AUTHORIZATION, authorizationHeader);
    }
}
