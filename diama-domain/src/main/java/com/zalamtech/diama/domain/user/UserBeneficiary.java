package com.zalamtech.diama.domain.user;

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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Bénéficiaire enregistré d'un utilisateur (destinataire d'un transfert). */
@Entity
@Table(name = "user_beneficiaries")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBeneficiary extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "nickname", length = 100)
    private String nickname;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /** Format E.164, ex. +224XXXXXXXXX. */
    @Column(name = "phone_msisdn", length = 30)
    private String phoneMsisdn;

    @Column(name = "bank_account_no", length = 50)
    private String bankAccountNo;

    @Column(name = "swift_bic", length = 11)
    private String swiftBic;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_iso", nullable = false, length = 2, columnDefinition = "char(2)")
    private String countryIso;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "thunes_payer_id")
    private Long thunesPayerId;

    /** 'MobileWallet' | 'BankAccount' | 'CashPickup'. */
    @Column(name = "payer_service", length = 50)
    private String payerService;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
