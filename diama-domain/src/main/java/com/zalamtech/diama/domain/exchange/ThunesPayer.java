package com.zalamtech.diama.domain.exchange;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
import java.time.OffsetDateTime;

/**
 * Payer Thunes (canal de payout). PK = identifiant natif Thunes ({@code BIGINT}),
 * assignée manuellement — pas de génération côté application.
 */
@Entity
@Table(name = "thunes_payers")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThunesPayer extends PanacheEntityBase {

    /** ID Thunes natif (assigné, non généré). */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_iso", nullable = false, length = 2, columnDefinition = "char(2)")
    private String countryIso;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency;

    /** 'MobileWallet' | 'BankAccount' | 'CashPickup'. */
    @Column(name = "service_type", nullable = false, length = 50)
    private String serviceType;

    @Column(name = "min_amount", precision = 18, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 18, scale = 2)
    private BigDecimal maxAmount;

    @Builder.Default
    @Column(name = "precision")
    private Short precision = 2;

    @Column(name = "increment", precision = 10, scale = 4)
    private BigDecimal increment;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    @CreationTimestamp
    @Column(name = "synced_at", nullable = false, updatable = false)
    private OffsetDateTime syncedAt;
}
