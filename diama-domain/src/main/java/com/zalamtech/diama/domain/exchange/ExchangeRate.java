package com.zalamtech.diama.domain.exchange;

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
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Taux de change d'un corridor (brut Thunes + marge → taux client). */
@Entity
@Table(name = "exchange_rates")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRate extends BaseEntity {

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "source_currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String sourceCurrency;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "dest_currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String destCurrency;

    @Column(name = "thunes_payer_id")
    private Long thunesPayerId;

    /** Taux brut. */
    @Column(name = "rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal rate;

    /** Marge appliquée (2 % par défaut). */
    @Builder.Default
    @Column(name = "spread_pct", nullable = false, precision = 5, scale = 4)
    private BigDecimal spreadPct = new BigDecimal("0.0200");

    /**
     * Colonne calculée en base : {@code rate * (1 - spread_pct)} (GENERATED ALWAYS … STORED).
     * En lecture seule côté application ; rechargée après insert/update.
     */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "client_rate", insertable = false, updatable = false, precision = 18, scale = 6)
    private BigDecimal clientRate;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @CreationTimestamp
    @Column(name = "fetched_at", nullable = false, updatable = false)
    private OffsetDateTime fetchedAt;
}
