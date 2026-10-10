package com.zalamtech.diama.infra.didit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zalamtech.diama.domain.kyc.DiditDocument;
import com.zalamtech.diama.domain.kyc.DiditGateway;
import com.zalamtech.diama.domain.kyc.DiditSession;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Adaptateur Didit : implémente le port {@link DiditGateway} (architecture hexagonale). */
@Slf4j
@ApplicationScoped
public class DiditClient implements DiditGateway {

    @Inject
    @RestClient
    DiditApiClient api;

    @Inject
    ObjectMapper objectMapper;

    /** URL de redirection en fin de parcours hébergé (deep link mobile) ; omise si vide. */
    @ConfigProperty(name = "diama.didit.callback-url")
    Optional<String> callbackUrl;

    @Override
    public DiditSession createSession(String workflowId, String vendorData) {
        final ObjectNode body = objectMapper.createObjectNode()
                .put("workflow_id", workflowId)
                .put("vendor_data", vendorData);
        callbackUrl.filter(url -> !url.isBlank()).ifPresent(url -> body.put("callback", url));

        final JsonNode node = read(api.createSession(write(body)));
        final String sessionId = node.path("session_id").asText(null);
        if (sessionId == null) {
            throw new IllegalStateException("Session Didit sans identifiant pour vendorData=" + vendorData);
        }
        log.info("Session Didit créée : {} (vendorData={}, workflow={})", sessionId, vendorData, workflowId);
        return new DiditSession(
                sessionId,
                node.path("session_token").asText(null),
                node.path("url").asText(null));
    }

    @Override
    public List<DiditDocument> getDocuments(String sessionId) {
        final JsonNode items = read(api.getDecision(sessionId)).path("id_verifications");
        final List<DiditDocument> documents = new ArrayList<>();
        if (items.isArray()) {
            for (JsonNode item : items) {
                documents.add(new DiditDocument(
                        item.path("node_id").asText(null),
                        item.path("document_type").asText(null),
                        item.path("issuing_state").asText(null),
                        item.path("status").asText(null)));
            }
        }
        return documents;
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Réponse Didit illisible", e);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Sérialisation de requête Didit impossible", e);
        }
    }
}
