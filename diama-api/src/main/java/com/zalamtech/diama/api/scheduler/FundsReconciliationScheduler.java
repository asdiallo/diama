package com.zalamtech.diama.api.scheduler;

import com.zalamtech.diama.domain.transaction.FundsVerificationService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Déclencheur de la réconciliation horaire Swan ↔ PostgreSQL (filet de
 * sécurité en cas de webhook manqué).
 */
@Slf4j
@ApplicationScoped
public class FundsReconciliationScheduler {

    @Inject
    FundsVerificationService fundsVerification;

    @Scheduled(every = "1h", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void reconcile() {
        try {
            fundsVerification.reconcile();
        } catch (RuntimeException e) {
            // Ne jamais laisser une exception s'échapper du scheduler (retry à la prochaine heure).
            log.error("Échec de la réconciliation Swan horaire", e);
        }
    }
}
