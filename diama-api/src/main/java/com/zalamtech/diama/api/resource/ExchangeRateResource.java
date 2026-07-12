package com.zalamtech.diama.api.resource;

import com.zalamtech.diama.common.dto.PayerDto;
import com.zalamtech.diama.common.dto.RateDto;
import com.zalamtech.diama.domain.exchange.ExchangeRate;
import com.zalamtech.diama.domain.exchange.ExchangeRateService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Consultation publique des taux de change et des opérateurs de payout.
 * Pas d'authentification requise (données de tarification affichées avant login).
 */
@Path("/api/v1/rates")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "rates")
public class ExchangeRateResource {

    @Inject
    ExchangeRateService exchangeRates;

    /** GET /current?from=EUR&to=GNF&payerId=1234 — taux du jour d'un corridor. */
    @Operation(summary = "Taux du jour d'un corridor")
    @GET
    @Path("/current")
    public RateDto current(@QueryParam("from") String from,
                           @QueryParam("to") String to,
                           @QueryParam("payerId") Long payerId) {
        final ExchangeRate rate = exchangeRates.getActiveRate(from, to, payerId);
        return new RateDto(
                rate.getSourceCurrency(),
                rate.getDestCurrency(),
                rate.getThunesPayerId(),
                rate.getRate(),
                rate.getClientRate(),
                rate.getEffectiveDate());
    }

    /** GET /payers?country=GN — opérateurs Thunes actifs d'un pays. */
    @Operation(summary = "Opérateurs de payout actifs d'un pays")
    @GET
    @Path("/payers")
    public List<PayerDto> payers(@QueryParam("country") String country) {
        return exchangeRates.getActivePayers(country).stream()
                .map(p -> new PayerDto(
                        p.getId(),
                        p.getName(),
                        p.getCountryIso(),
                        p.getCurrency(),
                        p.getServiceType(),
                        p.getMinAmount(),
                        p.getMaxAmount()))
                .toList();
    }
}
