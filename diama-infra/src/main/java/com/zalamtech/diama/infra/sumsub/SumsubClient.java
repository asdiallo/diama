package com.zalamtech.diama.infra.sumsub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zalamtech.diama.common.dto.SumsubReviewResult;
import com.zalamtech.diama.domain.kyc.SumsubApplicant;
import com.zalamtech.diama.domain.kyc.SumsubDocument;
import com.zalamtech.diama.domain.kyc.SumsubGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Adaptateur Sumsub : implémente le port {@link SumsubGateway} (architecture hexagonale). */
@Slf4j
@ApplicationScoped
public class SumsubClient implements SumsubGateway {

    private static final long TOKEN_TTL_SECONDS = Duration.ofMinutes(30).toSeconds();

    @Inject
    @RestClient
    SumsubApiClient api;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public SumsubApplicant createApplicant(String externalUserId, String levelName) {
        final String body = write(java.util.Map.of("externalUserId", externalUserId));
        final JsonNode node = read(api.createApplicant(levelName, body));
        final String applicantId = node.path("id").asText(null);
        if (applicantId == null) {
            throw new IllegalStateException("Applicant Sumsub sans identifiant pour externalUserId=" + externalUserId);
        }
        log.info("Applicant Sumsub créé : {} (externalUserId={}, niveau={})", applicantId, externalUserId, levelName);
        return new SumsubApplicant(applicantId, externalUserId);
    }

    @Override
    public String generateAccessToken(String applicantId) {
        // Simplification de modélisation : Sumsub attend en réalité le même
        // externalUserId qu'à la création de l'applicant (pas l'applicantId).
        // Dans cette intégration, externalUserId == users.id, distinct de
        // l'applicantId Sumsub ; à corriger lors du branchement réel en
        // conservant l'externalUserId d'origine si nécessaire.
        final JsonNode node = read(api.generateAccessToken(applicantId, null, TOKEN_TTL_SECONDS));
        final String token = node.path("token").asText(null);
        if (token == null) {
            throw new IllegalStateException("Jeton SDK Sumsub introuvable pour applicant=" + applicantId);
        }
        return token;
    }

    @Override
    public SumsubReviewResult getApplicantStatus(String applicantId) {
        final JsonNode node = read(api.getApplicantStatus(applicantId));
        final JsonNode reviewResult = node.path("reviewResult");
        return new SumsubReviewResult(
                applicantId,
                node.path("reviewStatus").asText(null),
                reviewResult.path("reviewAnswer").asText(null),
                toStringList(reviewResult.path("rejectLabels")),
                reviewResult.path("moderationComment").asText(null));
    }

    @Override
    public List<SumsubDocument> getApplicantDocuments(String applicantId) {
        final JsonNode items = read(api.getApplicantDocuments(applicantId)).path("items");
        final List<SumsubDocument> documents = new ArrayList<>();
        if (items.isArray()) {
            for (JsonNode item : items) {
                documents.add(new SumsubDocument(
                        item.path("id").asText(null),
                        item.path("idDocType").asText(null),
                        item.path("country").asText(null),
                        item.path("reviewResult").path("reviewAnswer").asText(item.path("status").asText(null))));
            }
        }
        return documents;
    }

    private static List<String> toStringList(JsonNode arrayNode) {
        final List<String> result = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            arrayNode.forEach(n -> result.add(n.asText()));
        }
        return result;
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Réponse Sumsub illisible", e);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Sérialisation de requête Sumsub impossible", e);
        }
    }
}
