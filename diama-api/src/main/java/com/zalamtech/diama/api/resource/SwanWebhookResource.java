package com.zalamtech.diama.api.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zalamtech.diama.common.dto.SwanWebhookEvent;
import com.zalamtech.diama.domain.audit.KafkaOutbox;
import com.zalamtech.diama.domain.audit.KafkaOutboxRepository;
import com.zalamtech.diama.domain.audit.OutboxStatus;
import com.zalamtech.diama.domain.transaction.Transaction;
import com.zalamtech.diama.domain.transaction.TransactionRepository;
import com.zalamtech.diama.domain.user.UserRepository;
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
import java.util.Set;
import java.util.UUID;

/**
 * Réception des webhooks Swan (réception / rejet de virements sur les comptes
 * dédiés).
 *
 * <p>Endpoint public (pas d'authentification Keycloak) : l'authenticité de
 * l'appelant est garantie par la vérification HMAC-SHA256 du corps de requête
 * (en-tête {@code X-Swan-Signature} ; l'API réelle Swan signe en JWS
 * asymétrique, simplifié ici en HMAC partagé par cohérence avec les autres
 * webhooks Diama). Le traitement métier est dispatché de façon asynchrone via
 * {@code kafka_outbox} (topic {@code swan-webhooks}), à l'identique de
 * {@code ThunesWebhookResource} / {@code SumsubWebhookResource}.</p>
 */
@Slf4j
@Path("/webhooks/swan")
@PermitAll
@Consumes(MediaType.APPLICATION_JSON)
public class SwanWebhookResource {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOPIC = "swan-webhooks";
    private static final String AGGREGATE_TYPE = "TRANSACTION";
    private static final Set<String> HANDLED_EVENT_TYPES = Set.of("TRANSACTION_BOOKED", "TRANSACTION_REJECTED");

    @Inject
    UserRepository users;

    @Inject
    TransactionRepository transactions;

    @Inject
    KafkaOutboxRepository outbox;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "diama.swan.webhook-secret")
    String webhookSecret;

    @POST
    @Transactional
    public Response receive(String rawBody, @HeaderParam("X-Swan-Signature") String signature) {
        if (!isValidSignature(rawBody, signature)) {
            log.warn("Webhook Swan rejeté : signature HMAC invalide");
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        final SwanWebhookEvent event;
        try {
            event = objectMapper.readValue(rawBody, SwanWebhookEvent.class);
        } catch (JsonProcessingException e) {
            log.warn("Webhook Swan rejeté : corps illisible", e);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (!HANDLED_EVENT_TYPES.contains(event.eventType())) {
            log.debug("Webhook Swan ignoré (type {})", event.eventType());
            return Response.ok().build();
        }

        final UUID transactionId = resolveTransaction(event).map(Transaction::getId).orElse(null);
        if (transactionId == null) {
            // Fonds potentiellement orphelins : laissés à FundsVerificationService (réconciliation horaire).
            log.warn("Webhook Swan {} sans transaction INITIATED correspondante (iban={}, montant={})",
                    event.eventType(), event.iban(), event.amount());
        }

        enqueue(transactionId, event);
        return Response.ok().build();
    }

    private Optional<Transaction> resolveTransaction(SwanWebhookEvent event) {
        if (event.iban() == null || event.amount() == null) {
            return Optional.empty();
        }
        return users.findBySwanIban(event.iban())
                .flatMap(u -> transactions.findFirstInitiatedByUserAndAmount(u.getId(), event.amount()));
    }

    private void enqueue(UUID transactionId, SwanWebhookEvent event) {
        final ObjectNode root = objectMapper.createObjectNode();
        root.put("transactionId", transactionId != null ? transactionId.toString() : null);
        root.put("eventType", event.eventType());
        root.put("swanAccountId", event.accountId());
        root.put("swanTransactionId", event.transactionId());
        root.put("amount", event.amount());
        root.put("currency", event.currency());
        root.put("rejectedReason", event.rejectedReason());

        outbox.persist(KafkaOutbox.builder()
                .aggregateId(transactionId != null ? transactionId : UUID.randomUUID())
                .aggregateType(AGGREGATE_TYPE)
                .eventType("SWAN_" + event.eventType())
                .payload(writePayload(root))
                .kafkaTopic(TOPIC)
                .status(OutboxStatus.PENDING)
                .build());

        log.info("Webhook Swan enfilé : {} tx={}", event.eventType(), transactionId);
    }

    private String writePayload(ObjectNode root) {
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            log.warn("Sérialisation du webhook Swan impossible", e);
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
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Vérification de signature Swan impossible", e);
            return false;
        }
    }
}
