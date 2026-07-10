package com.zalamtech.diama.domain.kyc;

/** Type PostgreSQL {@code kyc_verification_status}. */
public enum KycVerificationStatus {
    NOT_STARTED,
    PENDING,
    APPROVED,
    REJECTED,
    ON_HOLD
}
