package com.zalamtech.diama.infra.swan;

import com.zalamtech.diama.common.dto.SwanAccount;
import com.zalamtech.diama.common.dto.SwanAccountRequest;
import com.zalamtech.diama.common.dto.SwanTransfer;
import com.zalamtech.diama.common.dto.SwanTransferRequest;
import com.zalamtech.diama.domain.transaction.SwanGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Adaptateur Swan.io : implémente le port {@link SwanGateway} (architecture hexagonale). */
@Slf4j
@ApplicationScoped
public class SwanClient implements SwanGateway {

    @Inject
    @RestClient
    SwanApiClient api;

    @Override
    public String refund(String originalSwanTransferId, String destinationIban, String beneficiaryName,
                         BigDecimal amount, String currency, String reference) {
        final SwanRefundResponse response = api.refund(new SwanRefundRequest(
                originalSwanTransferId, destinationIban, beneficiaryName, amount, currency, reference));
        if (response == null || response.id() == null) {
            throw new IllegalStateException("Remboursement Swan sans identifiant (virement " + originalSwanTransferId + ")");
        }
        log.info("Remboursement Swan émis : {} (virement initial {})", response.id(), originalSwanTransferId);
        return response.id();
    }

    @Override
    public SwanAccount createAccount(SwanAccountRequest request) {
        final SwanAccount account = api.createAccount(request);
        if (account == null || account.accountId() == null || account.iban() == null) {
            throw new IllegalStateException("Compte Swan sans identifiant/IBAN pour externalUserId=" + request.externalUserId());
        }
        log.info("Compte Swan créé : {} (iban={}, externalUserId={})", account.accountId(), account.iban(), request.externalUserId());
        return account;
    }

    @Override
    public BigDecimal getAccountBalance(String accountId) {
        return api.getAccountBalance(accountId);
    }

    @Override
    public SwanTransfer initiateTransfer(SwanTransferRequest request) {
        final SwanTransfer transfer = api.initiateTransfer(request);
        if (transfer == null || transfer.transferId() == null) {
            throw new IllegalStateException("Virement Swan sans identifiant (compte " + request.sourceAccountId() + ")");
        }
        return transfer;
    }

    @Override
    public SwanTransfer getTransaction(String transactionId) {
        return api.getTransaction(transactionId);
    }

    @Override
    public List<SwanTransfer> listRecentTransactions(OffsetDateTime since) {
        return api.listTransactions(since.toString());
    }
}
