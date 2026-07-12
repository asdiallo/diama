package com.zalamtech.diama.flow;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Signatures HMAC-SHA256 des webhooks partenaires, calculées avec les secrets
 * factices du profil test ({@code diama.*.webhook-secret}).
 */
final class WebhookSignatures {

    static final String THUNES_SECRET = "test-thunes-webhook-secret";
    static final String SUMSUB_SECRET = "test-sumsub-webhook-secret";
    static final String SWAN_SECRET = "test-swan-webhook-secret";

    private WebhookSignatures() {
    }

    static String sign(String body, String secret) {
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
