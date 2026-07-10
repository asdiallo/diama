package com.zalamtech.diama.domain.audit;

import com.zalamtech.diama.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Événement d'audit — acteur final ou agent backoffice, avec contexte Keycloak complet. */
@Entity
@Table(name = "audit_events")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent extends BaseEntity {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "admin_id")
    private UUID adminId;

    @Column(name = "transaction_id")
    private UUID transactionId;

    // --- Contexte Keycloak (traçabilité) ---------------------------------
    /** {@code sub} du JWT. */
    @Column(name = "keycloak_user_id", length = 100)
    private String keycloakUserId;

    /** {@code sid} du JWT. */
    @Column(name = "keycloak_session_id", length = 100)
    private String keycloakSessionId;

    /** 'mobile-app' | 'backoffice-app'. */
    @Column(name = "keycloak_client", length = 100)
    private String keycloakClient;

    // --- Événement --------------------------------------------------------
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", columnDefinition = "jsonb")
    private String beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", columnDefinition = "jsonb")
    private String afterState;

    @ColumnTransformer(write = "?::inet")
    @Column(name = "ip_address", columnDefinition = "inet")
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;
}
