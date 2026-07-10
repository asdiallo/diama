package com.zalamtech.diama.api.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zalamtech.diama.domain.audit.KafkaOutbox;
import com.zalamtech.diama.domain.audit.KafkaOutboxRepository;
import com.zalamtech.diama.domain.audit.OutboxStatus;
import com.zalamtech.diama.domain.transaction.Transaction;
import com.zalamtech.diama.domain.transaction.TransactionRepository;
import com.zalamtech.diama.domain.transaction.TxStatus;
import com.zalamtech.diama.infra.thunes.ThunesCallbackPayload;
import com.zalamtech.diama.infra.thunes.ThunesStatusMapper;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Réception des callbacks Thunes (changement d'état d'une transaction de payout).
 *
 * <p>Endpoint public (pas d'authentification Keycloak) : l'authenticité de
 * l'appelant est garantie par la vérification HMAC-SHA256 du corps de requête
 * (en-tête {@code X-Thunes-Signature}). Le traitement métier est asynchrone —
 * ce endpoint se limite à vérifier, corréler la transaction et enfiler
 * l'événement dans {@code kafka_outbox} (topic {@code thunes-callbacks}), afin
 * de répondre {@code 200} bien en-deçà des 5 secondes attendues par Thunes.</p>
 */
@Slf4j
@Path("/webhooks/thunes")
@PermitAll
@Consumes(MediaType.APPLICATION_JSON)
public class ThunesWebhookResource {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOPIC = "thunes-callbacks";
    private static final String AGGREGATE_TYPE = "TRANSACTION";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TransactionRepository transactions;

    @Inject
    KafkaOutboxRepository outbox;

    @ConfigProperty(name = "diama.thunes.webhook-secret")
    String webhookSecret;

    @POST
    @Transactional
    public Response receive(String rawBody, @HeaderParam("X-Thunes-Signature") String signature) {
        if (!isValidSignature(rawBody, signature)) {
            log.warn("Callback Thunes rejeté : signature HMAC invalide");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        final ThunesCallbackPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, ThunesCallbackPayload.class);
        } catch (JsonProcessingException e) {
            log.warn("Callback Thunes rejeté : corps illisible", e);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        final TxStatus txStatus = ThunesStatusMapper.fromThunesCode(payload.status());
        if (txStatus == null) {
            log.warn("Callback Thunes ignoré : statut inconnu {} (externalId={})", payload.status(), payload.externalId());
            return Response.ok().build(); // ack — évite les tentatives de relivraison Thunes
        }

        final Transaction tx = payload.externalId() != null
                ? transactions.findByThunesExternalId(payload.externalId()).orElse(null)
                : null;
        if (tx == null) {
            log.error("Callback Thunes ignoré : transaction introuvable pour externalId={}", payload.externalId());
            return Response.ok().build();
        }

        enqueue(tx, payload, txStatus);
        return Response.ok().build();
    }

    private void enqueue(Transaction tx, ThunesCallbackPayload payload, TxStatus txStatus) {
        final ObjectNode root = objectMapper.createObjectNode();
        root.put("transactionId", tx.getId().toString());
        root.put("thunesExternalId", payload.externalId());
        root.put("txStatus", txStatus.name());
        root.put("statusMessage", payload.statusMessage());
        root.put("thunesTransactionId", payload.id());

        outbox.persist(KafkaOutbox.builder()
                .aggregateId(tx.getId())
                .aggregateType(AGGREGATE_TYPE)
                .eventType("THUNES_CALLBACK_" + txStatus.name())
                .payload(writePayload(root))
                .kafkaTopic(TOPIC)
                .status(OutboxStatus.PENDING)
                .build());

        log.info("Callback Thunes enfilé : tx={} → {}", tx.getId(), txStatus);
    }

    private String writePayload(ObjectNode root) {
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            // ObjectNode déjà en mémoire : échec improbable.
            log.warn("Sérialisation du callback Thunes impossible", e);
            return "{\"serializationError\":true}";
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
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            log.error("Vérification de signature Thunes impossible", e);
            return false;
        }
    }
}
