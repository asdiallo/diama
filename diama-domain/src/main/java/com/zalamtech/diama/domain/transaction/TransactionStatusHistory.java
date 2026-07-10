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

/** Historique des transitions de statut d'une transaction (audit métier). */
@Entity
@Table(name = "transaction_status_history")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionStatusHistory extends BaseEntity {

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @ColumnTransformer(write = "?::tx_status")
    @Column(name = "from_status", columnDefinition = "tx_status")
    private TxStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @ColumnTransformer(write = "?::tx_status")
    @Column(name = "to_status", nullable = false, columnDefinition = "tx_status")
    private TxStatus toStatus;

    /** 'WEBHOOK_THUNES' | 'USER' | 'ADMIN:{uuid}'. */
    @Column(name = "triggered_by", length = 50)
    private String triggeredBy;

    @Column(name = "admin_id")
    private UUID adminId;

    /** Payload brut Thunes, etc. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private OffsetDateTime changedAt;
}
