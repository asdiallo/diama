package com.zalamtech.diama.infra.swan;

import com.zalamtech.diama.domain.transaction.SwanGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.math.BigDecimal;

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
}
