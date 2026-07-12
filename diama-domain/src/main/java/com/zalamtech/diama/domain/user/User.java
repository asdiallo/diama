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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Utilisateur final. L'authentification est déléguée à Keycloak (pas de password_hash). */
@Entity
@Table(name = "users")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

    /** {@code sub} du JWT Keycloak — clé de jointure Keycloak ↔ DB. */
    @Column(name = "keycloak_id", nullable = false, unique = true, length = 100)
    private String keycloakId;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "phone", nullable = false, unique = true, length = 30)
    private String phone;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /** ISO 3166-1 alpha-3. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "nationality", length = 3, columnDefinition = "char(3)")
    private String nationality;

    /** ISO 3166-1 alpha-2. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_of_residence", nullable = false, length = 2, columnDefinition = "char(2)")
    private String countryOfResidence;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "preferred_currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String preferredCurrency = "EUR";

    @Builder.Default
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "user_status")
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    @Column(name = "swan_account_id", length = 100)
    private String swanAccountId;

    @Column(name = "swan_iban", length = 34)
    private String swanIban;

    @Column(name = "referral_code", unique = true, length = 20)
    private String referralCode;

    @Column(name = "referred_by")
    private UUID referredBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
