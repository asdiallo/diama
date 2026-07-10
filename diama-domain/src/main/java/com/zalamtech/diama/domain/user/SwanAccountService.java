package com.zalamtech.diama.domain.user;

import com.zalamtech.diama.common.dto.SwanAccount;
import com.zalamtech.diama.common.dto.SwanAccountRequest;
import com.zalamtech.diama.common.exception.ResourceNotFoundException;
import com.zalamtech.diama.domain.audit.AuditService;
import com.zalamtech.diama.domain.transaction.SwanGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Provisionnement et consultation du compte Swan dédié (IBAN individuel) d'un
 * utilisateur — déclenché à l'obtention du niveau KYC 2 (voir
 * {@code KycService#upgradeKycLevel}).
 */
@Slf4j
@ApplicationScoped
public class SwanAccountService {

    @Inject
    UserRepository users;

    @Inject
    SwanGateway swanGateway;

    @Inject
    AuditService audit;

    /** Crée le compte Swan de l'utilisateur s'il n'en a pas déjà un (idempotent). */
    @Transactional
    public SwanAccount provisionAccount(UUID userId) {
        final User user = requireUser(userId);

        if (user.getSwanAccountId() != null) {
            log.debug("Compte Swan déjà provisionné pour {}", userId);
            return toAccount(user, null);
        }

        final SwanAccount account = swanGateway.createAccount(new SwanAccountRequest(
                userId.toString(), user.getFirstName(), user.getLastName(), user.getEmail()));

        user.setSwanAccountId(account.accountId());
        user.setSwanIban(account.iban());

        audit.log(null, "SWAN_ACCOUNT_PROVISIONED", "USER", userId, null,
                Map.of("swanAccountId", account.accountId(), "iban", account.iban()));

        log.info("Compte Swan provisionné : user={} account={} iban={}", userId, account.accountId(), account.iban());
        return account;
    }

    /** Compte Swan de l'utilisateur (informations locales, pas d'appel Swan). */
    public SwanAccount getAccountForUser(UUID userId) {
        final User user = requireUser(userId);
        if (user.getSwanAccountId() == null) {
            throw new ResourceNotFoundException("Compte Swan", userId);
        }
        return toAccount(user, null);
    }

    /** {@code true} si le solde du compte Swan couvre au moins le montant attendu. */
    public boolean validateFundsReceived(String swanAccountId, BigDecimal expected) {
        final BigDecimal balance = swanGateway.getAccountBalance(swanAccountId);
        return balance != null && expected != null && balance.compareTo(expected) >= 0;
    }

    private User requireUser(UUID userId) {
        return users.findByIdOptional(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));
    }

    private static SwanAccount toAccount(User user, BigDecimal balance) {
        return new SwanAccount(
                user.getSwanAccountId(),
                user.getSwanIban(),
                null,
                user.getFirstName() + " " + user.getLastName(),
                balance,
                null);
    }
}
