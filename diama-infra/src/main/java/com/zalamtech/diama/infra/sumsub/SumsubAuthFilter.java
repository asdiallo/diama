package com.zalamtech.diama.infra.sumsub;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import org.eclipse.microprofile.config.ConfigProvider;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Authentification Sumsub : chaque requête est signée
 * {@code HMAC-SHA256(ts + méthode + chemin + corps)} avec la clé secrète de
 * l'app Sumsub, et porte les en-têtes {@code X-App-Token},
 * {@code X-App-Access-Sig} et {@code X-App-Access-Ts}.
 *
 * <p>Suppose que le corps des requêtes {@code SumsubApiClient} est toujours un
 * {@code String} JSON déjà sérialisé (voire vide) — les octets signés sont
 * alors garantis identiques aux octets effectivement transmis sur le fil,
 * contrairement à une resérialisation via un {@code ObjectMapper} local.</p>
 */
public class SumsubAuthFilter implements ClientRequestFilter {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String appToken;
    private final String appSecret;

    public SumsubAuthFilter() {
        final var config = ConfigProvider.getConfig();
        this.appToken = config.getOptionalValue("diama.sumsub.app-token", String.class).orElse("");
        this.appSecret = config.getOptionalValue("diama.sumsub.app-secret", String.class).orElse("");
    }

    @Override
    public void filter(ClientRequestContext requestContext) {
        final long ts = Instant.now().getEpochSecond();
        final String method = requestContext.getMethod();
        final String path = requestContext.getUri().getRawPath()
                + (requestContext.getUri().getRawQuery() != null ? "?" + requestContext.getUri().getRawQuery() : "");
        final byte[] body = requestContext.getEntity() instanceof String s
                ? s.getBytes(StandardCharsets.UTF_8)
                : new byte[0];

        final String signature = sign(ts + method + path, body);

        requestContext.getHeaders().putSingle("X-App-Token", appToken);
        requestContext.getHeaders().putSingle("X-App-Access-Sig", signature);
        requestContext.getHeaders().putSingle("X-App-Access-Ts", String.valueOf(ts));
    }

    private String sign(String prefix, byte[] body) {
        try {
            final Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            mac.update(prefix.getBytes(StandardCharsets.UTF_8));
            final byte[] computed = mac.doFinal(body);
            return HexFormat.of().formatHex(computed);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Signature HMAC Sumsub impossible", e);
        }
    }
}
