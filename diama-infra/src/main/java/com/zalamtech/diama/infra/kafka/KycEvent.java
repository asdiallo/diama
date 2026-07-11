package com.zalamtech.diama.infra.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Message du topic {@code kyc-events}, publié par le trigger SQL
 * {@code sync_kyc_level_to_keycloak} (clés en snake_case : {@code jsonb_build_object}
 * côté PostgreSQL, pas de sérialisation Jackson).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KycEvent(
        @JsonProperty("user_id") UUID userId,
        @JsonProperty("kyc_level") Integer kycLevel,
        @JsonProperty("verification_status") String verificationStatus,
        @JsonProperty("monthly_limit") BigDecimal monthlyLimit
) {
}
