package com.zalamtech.diama.domain.metrics;

import com.zalamtech.diama.domain.audit.KafkaOutboxRepository;
import com.zalamtech.diama.domain.audit.OutboxStatus;
import com.zalamtech.diama.domain.kyc.KycProfileRepository;
import com.zalamtech.diama.domain.kyc.KycVerificationStatus;
import com.zalamtech.diama.domain.transaction.RfiRequestRepository;
import com.zalamtech.diama.domain.transaction.RfiStatus;
import com.zalamtech.diama.domain.user.UserSessionRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;

/**
 * Métriques métier exposées via Micrometer ({@code /q/metrics}).
 *
 * <p>Deux familles :</p>
 * <ul>
 *   <li><b>Jauges</b> (état courant, interrogées à chaque scrape) : {@code kafka_outbox_pending_count},
 *       {@code active_sessions_count}, {@code rfi_pending_count}, {@code kyc_approval_rate}.</li>
 *   <li><b>Compteurs</b> (incrémentés à l'événement, tagués statut/corridor) : {@code transfer_count},
 *       {@code transfer_volume_eur} — alimentés par {@code TransferEventConsumer} (module {@code diama-infra})
 *       à chaque message {@code transfer-events}.</li>
 * </ul>
 */
@ApplicationScoped
public class MetricsService {

    @Inject
    MeterRegistry registry;

    @Inject
    KafkaOutboxRepository outbox;

    @Inject
    UserSessionRepository sessions;

    @Inject
    RfiRequestRepository rfiRequests;

    @Inject
    KycProfileRepository kycProfiles;

    @PostConstruct
    void registerGauges() {
        Gauge.builder("kafka_outbox_pending_count", outbox, r -> r.count("status", OutboxStatus.PENDING))
                .description("Entrées kafka_outbox en attente de publication")
                .register(registry);

        Gauge.builder("active_sessions_count", sessions, r -> r.count("isActive", true))
                .description("Sessions Keycloak actives")
                .register(registry);

        Gauge.builder("rfi_pending_count", rfiRequests, r -> r.count("status", RfiStatus.PENDING))
                .description("RFI Thunes en attente de réponse")
                .register(registry);

        Gauge.builder("kyc_approval_rate", this, MetricsService::kycApprovalRate)
                .description("Dossiers KYC approuvés / dossiers revus (approuvés + rejetés)")
                .register(registry);
    }

    private double kycApprovalRate() {
        final long approved = kycProfiles.count("verificationStatus", KycVerificationStatus.APPROVED);
        final long rejected = kycProfiles.count("verificationStatus", KycVerificationStatus.REJECTED);
        final long reviewed = approved + rejected;
        return reviewed == 0 ? 0.0 : (double) approved / reviewed;
    }

    /** Incrémente {@code transfer_count{status,corridor}} et, si livré, {@code transfer_volume_eur{corridor}}. */
    public void recordTransferEvent(String status, String corridor, BigDecimal deliveredAmount) {
        registry.counter("transfer_count", "status", status, "corridor", corridor).increment();
        if (deliveredAmount != null) {
            registry.counter("transfer_volume_eur", "corridor", corridor).increment(deliveredAmount.doubleValue());
        }
    }
}
