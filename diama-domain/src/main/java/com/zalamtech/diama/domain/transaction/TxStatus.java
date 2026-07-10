package com.zalamtech.diama.domain.transaction;

/** Type PostgreSQL {@code tx_status}. */
public enum TxStatus {
    INITIATED,
    AML_CLEARED,
    CONFIRMED,
    UNDER_REVIEW,
    PROCESSING,
    DELIVERED,
    FAILED,
    REFUNDED,
    CANCELLED
}
