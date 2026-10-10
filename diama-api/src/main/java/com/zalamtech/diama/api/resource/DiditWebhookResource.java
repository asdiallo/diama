package com.zalamtech.diama.api.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zalamtech.diama.domain.audit.KafkaOutbox;
import com.zalamtech.diama.domain.audit.KafkaOutboxRepository;
import com.zalamtech.diama.domain.audit.OutboxStatus;
import com.zalamtech.diama.domain.kyc.KycProfile;
import com.zalamtech.diama.domain.kyc.KycProfileRepository;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Réception des webhooks Didit v3 (changements de statut des sessions KYC).
 *
 * <p>Endpoint public (pas d'authentification Keycloak) : l'authenticité de
 * l'appelant est garantie par la vérification HMAC-SHA256 du corps brut
 * (en-tête {@code X-Signature}) et par la fraîcheur de l'horodatage
 * ({@code X-Timestamp}, ±5 minutes, anti-rejeu). Le traitement métier
 * ({@link com.zalamtech.diama.domain.kyc.KycService#handleDiditWebhook}) est
 * dispatché de façon asynchrone via {@code kafka_outbox} (topic
 * {@code didit-webhooks}), à l'identique de {@code ThunesWebhookResource}.</p>
 */
@Slf4j
@Path("/webhooks/didit")
@PermitAll
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "webhooks")
public class DiditWebhookResource {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOPIC = "didit-webhooks";
    private static final String AGGREGATE_TYPE = "KYC";
    /** Tolérance sur X-Timestamp, conforme à la recommandation Didit. */
    private static final long MAX_CLOCK_SKEW_SECONDS = 300;

    @Inject
    KycProfileRepository kycProfiles;

    @Inject
    KafkaOutboxRepository outbox;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "diama.didit.webhook-secret")
    String webhookSecret;

    @Operation(summary = "Webhook Didit (signature HMAC + horodatage, traitement asynchrone)")
    @POST
    @Transactional
    public Response receive(String rawBody,
                            @HeaderParam("X-Signature") String signature,
                            @HeaderParam("X-Timestamp") String timestamp) {
        if (!isFresh(timestamp)) {
            log.warn("Webhook Didit rejeté : X-Timestamp absent ou hors tolérance ({})", timestamp);
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        if (!isValidSignature(rawBody, signature)) {
            log.warn("Webhook Didit rejeté : signature HMAC invalide");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        if (rawBody == null || rawBody.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        final UUID aggregateId = resolveSessionId(rawBody)
                .flatMap(kycProfiles::findByDiditSessionId)
                .map(KycProfile::getId)
                .orElseGet(UUID::randomUUID);

        outbox.persist(KafkaOutbox.builder()
                .aggregateId(aggregateId)
                .aggregateType(AGGREGATE_TYPE)
                .eventType("DIDIT_WEBHOOK")
                .payload(rawBody)
                .kafkaTopic(TOPIC)
                .status(OutboxStatus.PENDING)
                .build());

        log.info("Webhook Didit enfilé (aggregateId={})", aggregateId);
        return Response.ok().build();
    }

    private Optional<String> resolveSessionId(String rawBody) {
        try {
            final JsonNode node = objectMapper.readTree(rawBody).path("session_id");
            return node.isTextual() ? Optional.of(node.asText()) : Optional.empty();
        } catch (Exception e) {
            log.warn("Corps de webhook Didit illisible pour extraction de session_id", e);
            return Optional.empty();
        }
    }

    private static boolean isFresh(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return false;
        }
        try {
            final long skew = Math.abs(Instant.now().getEpochSecond() - Long.parseLong(timestamp.trim()));
            return skew <= MAX_CLOCK_SKEW_SECONDS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isValidSignature(String rawBody, String signature) {
        if (signature == null || signature.isBlank() || rawBody == null) {
            return false;
        }
        try {
            final Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            final byte[] computed = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            final String computedHex = HexFormat.of().formatHex(computed);
            return MessageDigest.isEqual(
                    computedHex.getBytes(StandardCharsets.UTF_8),
                    signature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Vérification de signature Didit impossible", e);
            return false;
        }
    }
}
