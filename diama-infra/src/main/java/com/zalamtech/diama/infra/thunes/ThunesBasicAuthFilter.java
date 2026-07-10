package com.zalamtech.diama.infra.thunes;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import org.eclipse.microprofile.config.ConfigProvider;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Filtre d'authentification Thunes v2 : ajoute l'en-tête
 * {@code Authorization: Basic base64(api-key:api-secret)} à chaque requête.
 *
 * <p>Les identifiants sont lus depuis {@code diama.thunes.api-key} et
 * {@code diama.thunes.api-secret} (application.yaml → variables d'env). L'en-tête
 * est calculé une fois à la construction du filtre (les secrets ne changent pas à
 * chaud). Enregistré sur le client via {@code @RegisterProvider} sur
 * {@link ThunesApiClient}.</p>
 */
public class ThunesBasicAuthFilter implements ClientRequestFilter {

    private final String authorizationHeader;

    public ThunesBasicAuthFilter() {
        final var config = ConfigProvider.getConfig();
        final String apiKey = config.getOptionalValue("diama.thunes.api-key", String.class).orElse("");
        final String apiSecret = config.getOptionalValue("diama.thunes.api-secret", String.class).orElse("");
        final String token = Base64.getEncoder()
                .encodeToString((apiKey + ":" + apiSecret).getBytes(StandardCharsets.UTF_8));
        this.authorizationHeader = "Basic " + token;
    }

    @Override
    public void filter(ClientRequestContext requestContext) {
        requestContext.getHeaders().putSingle(HttpHeaders.AUTHORIZATION, authorizationHeader);
    }
}
