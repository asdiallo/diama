package com.zalamtech.diama.domain.user;

import com.zalamtech.diama.common.dto.AdminTransactionDto;
import com.zalamtech.diama.common.dto.AdminUserDetailDto;
import com.zalamtech.diama.common.dto.AdminUserListItemDto;
import com.zalamtech.diama.common.exception.ResourceNotFoundException;
import com.zalamtech.diama.domain.audit.AuditService;
import com.zalamtech.diama.domain.kyc.KycProfile;
import com.zalamtech.diama.domain.kyc.KycProfileRepository;
import com.zalamtech.diama.domain.transaction.AdminTransactionMapper;
import com.zalamtech.diama.domain.transaction.TransactionRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Consultation et administration des comptes utilisateurs (backoffice). */
@Slf4j
@ApplicationScoped
public class AdminUserService {

    private static final int RECENT_TRANSACTIONS_LIMIT = 10;

    @Inject
    UserRepository users;

    @Inject
    KycProfileRepository kycProfiles;

    @Inject
    TransactionRepository transactions;

    @Inject
    UserSessionRepository sessions;

    @Inject
    UserSessionService userSessionService;

    @Inject
    AuditService audit;

    public List<AdminUserListItemDto> search(UserStatus status, String q, Sort sort, int page, int size) {
        return users.searchAdmin(status, q, sort, page, size).stream()
                .map(AdminUserService::toListItem)
                .toList();
    }

    public long count(UserStatus status, String q) {
        return users.countAdmin(status, q);
    }

    public AdminUserDetailDto getDetail(UUID userId) {
        final User user = requireUser(userId);
        final KycProfile kyc = kycProfiles.findByUserId(userId).orElse(null);
        final List<AdminTransactionDto> recent = transactions.findByUserIdPaginated(userId, 0, RECENT_TRANSACTIONS_LIMIT)
                .stream()
                .map(AdminTransactionMapper::toDto)
                .toList();
        return toDetailDto(user, kyc, recent);
    }

    @Transactional
    public void updateStatus(UUID userId, UserStatus newStatus, UUID adminId) {
        final User user = requireUser(userId);
        final UserStatus before = user.getStatus();
        user.setStatus(newStatus);

        audit.log(null, userId, adminId, "USER_STATUS_CHANGED", "USER", userId,
                Map.of("status", before.name()), Map.of("status", newStatus.name()));

        log.info("Statut utilisateur modifié : user={} {} → {} (admin={})", userId, before, newStatus, adminId);
    }

    public List<AdminTransactionDto> listTransactions(UUID userId, int page, int size) {
        requireUser(userId);
        return transactions.findByUserIdPaginated(userId, page, size).stream()
                .map(AdminTransactionMapper::toDto)
                .toList();
    }

    public long countTransactions(UUID userId) {
        return transactions.count("userId", userId);
    }

    public List<UserSession> listSessions(UUID userId) {
        requireUser(userId);
        return sessions.findActiveByUserId(userId);
    }

    public void revokeSession(UUID userId, String keycloakSessionId, UUID adminId) {
        final UserSession session = sessions.findByKeycloakSessionId(keycloakSessionId)
                .filter(s -> userId.equals(s.getUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("Session introuvable pour cet utilisateur : " + keycloakSessionId));
        userSessionService.endSession(session.getKeycloakSessionId(), adminId);
    }

    private User requireUser(UUID userId) {
        return users.findByIdOptional(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));
    }

    private static AdminUserListItemDto toListItem(User u) {
        return new AdminUserListItemDto(
                u.getId(), u.getEmail(), u.getPhone(), u.getFirstName(), u.getLastName(),
                u.getStatus() != null ? u.getStatus().name() : null, u.getCountryOfResidence(), u.getCreatedAt());
    }

    private static AdminUserDetailDto toDetailDto(User u, KycProfile kyc, List<AdminTransactionDto> recent) {
        return new AdminUserDetailDto(
                u.getId(), u.getKeycloakId(), u.getEmail(), u.getPhone(), u.getFirstName(), u.getLastName(),
                u.getDateOfBirth(), u.getNationality(), u.getCountryOfResidence(), u.getPreferredCurrency(),
                u.getStatus() != null ? u.getStatus().name() : null, u.getSwanIban(), u.getSwanAccountId(),
                u.getReferralCode(),
                kyc != null ? Integer.valueOf(kyc.getKycLevel()) : null,
                kyc != null && kyc.getVerificationStatus() != null ? kyc.getVerificationStatus().name() : null,
                kyc != null ? kyc.getMonthlyLimit() : null,
                kyc != null ? kyc.getMonthlyUsed() : null,
                u.getCreatedAt(),
                recent);
    }
}
