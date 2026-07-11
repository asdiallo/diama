package com.zalamtech.diama.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Transition manuelle backoffice d'une transaction — le motif est obligatoire
 * (tracé dans {@code transaction_status_history} et {@code audit_events}).
 *
 * @param status {@code INITIATED} | {@code AML_CLEARED} | {@code CONFIRMED} | {@code UNDER_REVIEW} |
 *               {@code PROCESSING} | {@code DELIVERED} | {@code FAILED} | {@code REFUNDED} | {@code CANCELLED}
 * @param reason motif de la transition manuelle
 */
public record ForceStatusRequest(
        @NotNull
        @Pattern(regexp = "^(INITIATED|AML_CLEARED|CONFIRMED|UNDER_REVIEW|PROCESSING|DELIVERED|FAILED|REFUNDED|CANCELLED)$",
                message = "statut invalide")
        String status,

        @NotBlank
        String reason
) {
}
