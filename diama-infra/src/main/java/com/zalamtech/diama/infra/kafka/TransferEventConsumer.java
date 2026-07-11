package com.zalamtech.diama.infra.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zalamtech.diama.domain.kyc.KycProfileRepository;
import com.zalamtech.diama.domain.metrics.MetricsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

/**
 * Consomme le topic {@code transfer-events} (chaque transition FSM, publié par
 * {@code TransactionFSM}/{@code TransferService}) : alimente les métriques
 * {@code transfer_count} / {@code transfer_volume_eur} et incrémente
 * {@code kyc_profiles.monthly_used} à chaque livraison.
 */
@Slf4j
@ApplicationScoped
public class TransferEventConsumer {

    private static final String STATUS_DELIVERED = "DELIVERED";

    @Inject
    KycProfileRepository kycProfiles;

    @Inject
    MetricsService metrics;

    @Inject
    ObjectMapper objectMapper;

    @Incoming("transfer-events")
    @Transactional
    public void onMessage(String json) {
        final TransferEvent event = parse(json);
        if (event == null || event.status() == null) {
            log.warn("Événement transfer-events ignoré (illisible) : {}", json);
            return;
        }

        final String corridor = event.sourceCurrency() + "-" + event.destCurrency();
        final boolean delivered = STATUS_DELIVERED.equals(event.status());
        metrics.recordTransferEvent(event.status(), corridor, delivered ? event.sourceAmount() : null);

        if (delivered && event.userId() != null && event.sourceAmount() != null) {
            kycProfiles.incrementMonthlyUsed(event.userId(), event.sourceAmount());
        }
    }

    private TransferEvent parse(String json) {
        try {
            return objectMapper.readValue(json, TransferEvent.class);
        } catch (Exception e) {
            log.error("Message transfer-events illisible", e);
            return null;
        }
    }
}
