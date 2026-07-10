package com.zalamtech.diama.domain.kyc;

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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Screening AML d'un utilisateur / d'une transaction (Thunes Fortress). */
@Entity
@Table(name = "aml_screenings")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmlScreening extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Builder.Default
    @Column(name = "provider", nullable = false, length = 50)
    private String provider = "THUNES_FORTRESS";

    @Enumerated(EnumType.STRING)
    @ColumnTransformer(write = "?::aml_result")
    @Column(name = "result", nullable = false, columnDefinition = "aml_result")
    private AmlResult result;

    @Column(name = "risk_score", precision = 5, scale = 2)
    private BigDecimal riskScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matched_lists", columnDefinition = "jsonb")
    private String matchedLists;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", columnDefinition = "jsonb")
    private String rawResponse;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @CreationTimestamp
    @Column(name = "screened_at", nullable = false, updatable = false)
    private OffsetDateTime screenedAt;
}
