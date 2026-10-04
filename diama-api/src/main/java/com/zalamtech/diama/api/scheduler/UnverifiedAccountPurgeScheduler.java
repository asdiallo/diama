package com.zalamtech.diama.api.scheduler;

import com.zalamtech.diama.domain.user.UserRegistrationService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Purge quotidienne des inscriptions dont l'email n'a jamais été confirmé
 * (voir {@link UserRegistrationService#purgeUnverified()}). S'exécute chaque
 * jour à 03:30 UTC.
 */
@Slf4j
@ApplicationScoped
public class UnverifiedAccountPurgeScheduler {

    @Inject
    UserRegistrationService registration;

    @Scheduled(cron = "0 30 3 * * ?", timeZone = "UTC",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void purgeUnverifiedAccounts() {
        try {
            final UserRegistrationService.PurgeReport report = registration.purgeUnverified();
            log.info("Purge des inscriptions non confirmées : {} supprimées, {} confirmées entre-temps, {} en échec",
                    report.purged(), report.confirmed(), report.failed());
        } catch (RuntimeException e) {
            // Ne jamais laisser une exception s'échapper du scheduler (reprise le lendemain).
            log.error("Échec de la purge des inscriptions non confirmées", e);
        }
    }
}
