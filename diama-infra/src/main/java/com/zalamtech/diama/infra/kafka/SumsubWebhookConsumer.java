package com.zalamtech.diama.infra.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zalamtech.diama.common.dto.SumsubWebhookPayload;
import com.zalamtech.diama.domain.kyc.KycService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

/**
 * Consomme les webhooks Sumsub enfilés par {@code SumsubWebhookResource} (topic
 * {@code sumsub-webhooks}) : traitement métier découplé de la réponse HTTP
 * envoyée à Sumsub.
 */
@Slf4j
@ApplicationScoped
public class SumsubWebhookConsumer {

    @Inject
    KycService kycService;

    @Inject
    ObjectMapper objectMapper;

    @Incoming("sumsub-webhooks")
    @Transactional
    public void onMessage(String json) {
        try {
            final SumsubWebhookPayload payload = objectMapper.readValue(json, SumsubWebhookPayload.class);
            kycService.handleSumsubWebhook(payload);
        } catch (Exception e) {
            log.error("Traitement du webhook Sumsub échoué : {}", json, e);
        }
    }
}
