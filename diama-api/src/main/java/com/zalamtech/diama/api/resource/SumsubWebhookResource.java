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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Réception des webhooks Sumsub (verdicts de revue KYC, documents, …).
 *
 * <p>Endpoint public (pas d'authentification Keycloak) : l'authenticité de
 * l'appelant est garantie par la vérification HMAC-SHA256 du corps de requête
 * (en-tête {@code X-App-Access-Sig}). Le traitement métier
 * ({@link com.zalamtech.diama.domain.kyc.KycService#handleSumsubWebhook}) est
 * dispatché de façon asynchrone via {@code kafka_outbox} (topic
 * {@code sumsub-webhooks}), à l'identique de {@code ThunesWebhookResource}.</p>
 */
@Slf4j
@Path("/webhooks/sumsub")
@PermitAll
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "webhooks")
public class SumsubWebhookResource {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOPIC = "sumsub-webhooks";
    private static final String AGGREGATE_TYPE = "KYC";

    @Inject
    KycProfileRepository kycProfiles;

    @Inject
    KafkaOutboxRepository outbox;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "diama.sumsub.webhook-secret")
    String webhookSecret;

    @Operation(summary = "Webhook Sumsub (signature HMAC, traitement asynchrone)")
    @POST
    @Transactional
    public Response receive(String rawBody, @HeaderParam("X-App-Access-Sig") String signature) {
        if (!isValidSignature(rawBody, signature)) {
            log.warn("Webhook Sumsub rejeté : signature HMAC invalide");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        if (rawBody == null || rawBody.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        final UUID aggregateId = resolveApplicantId(rawBody)
                .flatMap(kycProfiles::findBySumsubApplicantId)
                .map(KycProfile::getId)
                .orElseGet(UUID::randomUUID);

        outbox.persist(KafkaOutbox.builder()
                .aggregateId(aggregateId)
                .aggregateType(AGGREGATE_TYPE)
                .eventType("SUMSUB_WEBHOOK")
                .payload(rawBody)
                .kafkaTopic(TOPIC)
                .status(OutboxStatus.PENDING)
                .build());

        log.info("Webhook Sumsub enfilé (aggregateId={})", aggregateId);
        return Response.ok().build();
    }

    private Optional<String> resolveApplicantId(String rawBody) {
        try {
            final JsonNode node = objectMapper.readTree(rawBody).path("applicantId");
            return node.isTextual() ? Optional.of(node.asText()) : Optional.empty();
        } catch (Exception e) {
            log.warn("Corps de webhook Sumsub illisible pour extraction d'applicantId", e);
            return Optional.empty();
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
            log.error("Vérification de signature Sumsub impossible", e);
            return false;
        }
    }
}
