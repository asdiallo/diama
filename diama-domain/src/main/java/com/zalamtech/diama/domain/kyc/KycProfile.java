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
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

/** Profil KYC / conformité d'un utilisateur (niveau, plafonds, statut de vérification). */
@Entity
@Table(name = "kyc_profiles")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycProfile extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Builder.Default
    @Column(name = "kyc_level", nullable = false)
    private Short kycLevel = 1;

    @Column(name = "sumsub_applicant_id", unique = true, length = 100)
    private String sumsubApplicantId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @ColumnTransformer(write = "?::kyc_verification_status")
    @Column(name = "verification_status", nullable = false, columnDefinition = "kyc_verification_status")
    private KycVerificationStatus verificationStatus = KycVerificationStatus.NOT_STARTED;

    @Column(name = "risk_score")
    private Short riskScore;

    /** 'LOW' | 'MEDIUM' | 'HIGH'. */
    @Column(name = "risk_category", length = 20)
    private String riskCategory;

    @Builder.Default
    @Column(name = "monthly_limit", nullable = false, precision = 18, scale = 2)
    private BigDecimal monthlyLimit = new BigDecimal("300.00");

    @Builder.Default
    @Column(name = "monthly_used", nullable = false, precision = 18, scale = 2)
    private BigDecimal monthlyUsed = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "limit_reset_date", nullable = false)
    private LocalDate limitResetDate = LocalDate.now()
            .with(TemporalAdjusters.firstDayOfNextMonth());

    @Column(name = "level_granted_at")
    private OffsetDateTime levelGrantedAt;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
