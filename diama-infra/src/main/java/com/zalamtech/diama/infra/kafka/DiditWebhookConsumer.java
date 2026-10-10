package com.zalamtech.diama.infra.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zalamtech.diama.common.dto.DiditWebhookPayload;
import com.zalamtech.diama.domain.kyc.KycService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

/**
 * Consomme les webhooks Didit enfilés par {@code DiditWebhookResource} (topic
 * {@code didit-webhooks}) : traitement métier découplé de la réponse HTTP
 * envoyée à Didit.
 */
@Slf4j
@ApplicationScoped
public class DiditWebhookConsumer {

    @Inject
    KycService kycService;

    @Inject
    ObjectMapper objectMapper;

    @Incoming("didit-webhooks")
    @Transactional
    public void onMessage(String json) {
        try {
            final DiditWebhookPayload payload = objectMapper.readValue(json, DiditWebhookPayload.class);
            kycService.handleDiditWebhook(payload);
        } catch (Exception e) {
            log.error("Traitement du webhook Didit échoué : {}", json, e);
        }
    }
}
