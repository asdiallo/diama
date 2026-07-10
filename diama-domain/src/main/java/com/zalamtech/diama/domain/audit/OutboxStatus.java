package com.zalamtech.diama.domain.audit;

/** Type PostgreSQL {@code outbox_status}. */
public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
