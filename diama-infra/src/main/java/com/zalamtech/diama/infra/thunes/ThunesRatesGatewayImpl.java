package com.zalamtech.diama.infra.thunes;

import com.zalamtech.diama.domain.exchange.ThunesRateQuote;
import com.zalamtech.diama.domain.exchange.ThunesRatesGateway;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;

/**
 * Adaptateur REST du port {@link ThunesRatesGateway} : traduit les réponses
 * Thunes en cotations du domaine.
 */
@ApplicationScoped
public class ThunesRatesGatewayImpl implements ThunesRatesGateway {

    @Inject
    @RestClient
    ThunesRateApiClient client;

    @Override
    public List<ThunesRateQuote> fetchActiveRates() {
        return client.getRates().stream()
                .map(r -> new ThunesRateQuote(r.payerId(), r.source(), r.destination(), r.wholesaleRate()))
                .toList();
    }
}
