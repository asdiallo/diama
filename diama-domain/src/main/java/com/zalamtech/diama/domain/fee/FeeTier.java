package com.zalamtech.diama.domain.fee;

import com.zalamtech.diama.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Palier de frais applicable à un corridor / une tranche de montant. */
@Entity
@Table(name = "fee_tiers")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeTier extends BaseEntity {

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "source_currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String sourceCurrency;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "dest_currency", length = 3, columnDefinition = "char(3)")
    private String destCurrency;

    @Column(name = "min_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 18, scale = 2)
    private BigDecimal maxAmount;

    @Builder.Default
    @Column(name = "fixed_fee", nullable = false, precision = 18, scale = 2)
    private BigDecimal fixedFee = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "percent_fee", nullable = false, precision = 5, scale = 4)
    private BigDecimal percentFee = BigDecimal.ZERO;

    @Column(name = "min_fee", precision = 18, scale = 2)
    private BigDecimal minFee;

    @Column(name = "max_fee", precision = 18, scale = 2)
    private BigDecimal maxFee;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    @Builder.Default
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom = LocalDate.now();

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
