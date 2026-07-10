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

import java.time.OffsetDateTime;
import java.util.UUID;

/** Document justificatif KYC rattaché à un profil (source Sumsub). */
@Entity
@Table(name = "kyc_documents")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycDocument extends BaseEntity {

    @Column(name = "kyc_profile_id", nullable = false)
    private UUID kycProfileId;

    @Enumerated(EnumType.STRING)
    @ColumnTransformer(write = "?::doc_type")
    @Column(name = "doc_type", nullable = false, columnDefinition = "doc_type")
    private DocType docType;

    @Column(name = "sumsub_doc_id", length = 100)
    private String sumsubDocId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @ColumnTransformer(write = "?::doc_status")
    @Column(name = "status", nullable = false, columnDefinition = "doc_status")
    private DocStatus status = DocStatus.PENDING;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
