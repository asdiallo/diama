package com.zalamtech.diama.domain.kyc;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;

/**
 * Correspondance niveau KYC Diama (1 à 4) ↔ workflow Didit (UUID publié dans la
 * console), configurée par {@code diama.didit.workflows.level-N}.
 */
@ApplicationScoped
public class KycWorkflows {

    @ConfigProperty(name = "diama.didit.workflows.level-1")
    String level1;

    @ConfigProperty(name = "diama.didit.workflows.level-2")
    String level2;

    @ConfigProperty(name = "diama.didit.workflows.level-3")
    String level3;

    @ConfigProperty(name = "diama.didit.workflows.level-4")
    String level4;

    /** Workflow à lancer pour atteindre le niveau donné (borné à 1..MAX_LEVEL). */
    public String workflowId(int level) {
        return workflows().get(Math.max(1, Math.min(level, KycLevels.MAX_LEVEL)) - 1);
    }

    /**
     * Niveau correspondant à un workflow, s'il est attribué à un seul niveau ;
     * vide sinon (workflow inconnu ou partagé entre plusieurs niveaux).
     */
    public Optional<Integer> levelOf(String workflowId) {
        if (workflowId == null) {
            return Optional.empty();
        }
        final List<String> workflows = workflows();
        Integer match = null;
        for (int i = 0; i < workflows.size(); i++) {
            if (workflowId.equals(workflows.get(i))) {
                if (match != null) {
                    return Optional.empty();
                }
                match = i + 1;
            }
        }
        return Optional.ofNullable(match);
    }

    private List<String> workflows() {
        return List.of(level1, level2, level3, level4);
    }
}
