package com.zalamtech.diama.domain.transaction;

import com.zalamtech.diama.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** Request For Information Thunes — demande d'informations complémentaires (compliance). */
@Entity
@Table(name = "rfi_requests")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RfiRequest extends BaseEntity {

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "thunes_rfi_id", nullable = false)
    private Long thunesRfiId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @ColumnTransformer(write = "?::rfi_status")
    @Column(name = "status", nullable = false, columnDefinition = "rfi_status")
    private RfiStatus status = RfiStatus.PENDING;

    /** Ex. {"sender":["date_of_birth"]}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requested_info", columnDefinition = "jsonb")
    private String requestedInfo;

    /** Ex. ["identification_documents"]. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requested_docs", columnDefinition = "jsonb")
    private String requestedDocs;

    @Column(name = "thunes_description", columnDefinition = "text")
    private String thunesDescription;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
