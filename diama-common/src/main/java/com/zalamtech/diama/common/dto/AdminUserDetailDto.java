package com.zalamtech.diama.common.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Dossier complet d'un utilisateur (backoffice) : profil, KYC, transferts récents. */
public record AdminUserDetailDto(
        UUID id,
        String keycloakId,
        String email,
        String phone,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String nationality,
        String countryOfResidence,
        String preferredCurrency,
        String status,
        String swanIban,
        String swanAccountId,
        String referralCode,
        Integer kycLevel,
        String kycVerificationStatus,
        BigDecimal monthlyLimit,
        BigDecimal monthlyUsed,
        OffsetDateTime createdAt,
        List<AdminTransactionDto> recentTransactions
) {
}
