package com.zalamtech.diama.domain.transaction;

import com.zalamtech.diama.domain.support.BaseEntity;
import com.zalamtech.diama.domain.user.SessionClient;
import com.zalamtech.diama.domain.user.SessionClientConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Transaction de transfert Europe → Afrique (collecte Swan, payout Thunes). */
@Entity
@Table(name = "transactions")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "beneficiary_id", nullable = false)
    private UUID beneficiaryId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @ColumnTransformer(write = "?::tx_status")
    @Column(name = "status", nullable = false, columnDefinition = "tx_status")
    private TxStatus status = TxStatus.INITIATED;

    // --- Montants (BigDecimal — jamais de double pour de la monnaie) ------
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "source_currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String sourceCurrency;

    @Column(name = "source_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal sourceAmount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "dest_currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String destCurrency;

    @Column(name = "dest_amount", precision = 18, scale = 2)
    private BigDecimal destAmount;

    @Column(name = "exchange_rate", precision = 18, scale = 6)
    private BigDecimal exchangeRate;

    @Column(name = "fee_amount", precision = 18, scale = 2)
    private BigDecimal feeAmount;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "fee_currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String feeCurrency = "EUR";

    // --- Références Thunes ------------------------------------------------
    @Column(name = "thunes_quotation_id")
    private Long thunesQuotationId;

    @Column(name = "thunes_transaction_id")
    private Long thunesTransactionId;

    /** Notre UUID envoyé à Thunes (idempotence). */
    @Column(name = "thunes_external_id", unique = true, length = 100)
    private String thunesExternalId;

    @Column(name = "thunes_payer_id")
    private Long thunesPayerId;

    // --- Références Swan (collecte EUR) -----------------------------------
    @Column(name = "swan_transfer_id", length = 100)
    private String swanTransferId;

    @Column(name = "swan_account_id", length = 100)
    private String swanAccountId;

    // --- Contexte Keycloak de la session initiatrice ---------------------
    @Column(name = "keycloak_session_id", length = 100)
    private String keycloakSessionId;

    @Convert(converter = SessionClientConverter.class)
    @ColumnTransformer(write = "?::session_client")
    @Column(name = "initiator_client", columnDefinition = "session_client")
    private SessionClient initiatorClient;

    // --- Métadonnées ------------------------------------------------------
    @ColumnTransformer(write = "?::inet")
    @Column(name = "sender_ip", columnDefinition = "inet")
    private String senderIp;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "refund_amount", precision = 18, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "refund_at")
    private OffsetDateTime refundAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}
