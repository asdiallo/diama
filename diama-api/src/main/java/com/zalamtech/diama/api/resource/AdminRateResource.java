package com.zalamtech.diama.api.resource;

import com.zalamtech.diama.api.security.SecurityContext;
import com.zalamtech.diama.common.dto.AdminRateDto;
import com.zalamtech.diama.common.dto.Page;
import com.zalamtech.diama.common.dto.UpdateSpreadRequest;
import com.zalamtech.diama.domain.exchange.AdminRateService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Administration des taux de change (rôle {@code admin}).
 *
 * <ul>
 *   <li>{@code GET  /rates}              — taux actifs par corridor</li>
 *   <li>{@code PUT  /rates/{id}/spread}  — modifier la marge</li>
 *   <li>{@code POST /rates/refresh}      — refresh manuel depuis Thunes</li>
 *   <li>{@code GET  /rates/history}      — historique d'un corridor ({@code ?corridor=EUR-GNF})</li>
 * </ul>
 */
@Path("/api/v1/admin/rates")
@RolesAllowed("admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminRateResource {

    private static final int MAX_PAGE_SIZE = 100;

    @Inject
    AdminRateService adminRates;

    @Inject
    SecurityContext security;

    @GET
    public List<AdminRateDto> list() {
        return adminRates.listActive();
    }

    @PUT
    @Path("/{id}/spread")
    public AdminRateDto updateSpread(@PathParam("id") UUID id, @Valid UpdateSpreadRequest request) {
        return adminRates.updateSpread(id, request.spreadPct(), security.getCurrentAdminId());
    }

    @POST
    @Path("/refresh")
    public Map<String, Integer> refresh() {
        return Map.of("corridorsUpdated", adminRates.refresh(security.getCurrentAdminId()));
    }

    @GET
    @Path("/history")
    public Page<AdminRateDto> history(@QueryParam("corridor") String corridor,
                                      @QueryParam("page") @DefaultValue("0") int page,
                                      @QueryParam("size") @DefaultValue("20") int size) {
        final String[] parts = splitCorridor(corridor);
        final int clampedPage = Math.max(0, page);
        final int clampedSize = clampSize(size);

        final List<AdminRateDto> content = adminRates.history(parts[0], parts[1], clampedPage, clampedSize);
        final long total = adminRates.countHistory(parts[0], parts[1]);
        return Page.of(content, clampedPage, clampedSize, total);
    }

    private static String[] splitCorridor(String corridor) {
        if (corridor == null || !corridor.contains("-")) {
            throw new WebApplicationException("Paramètre 'corridor' invalide, attendu SOURCE-DEST (ex. EUR-GNF)", 400);
        }
        final String[] parts = corridor.split("-", 2);
        return new String[] {parts[0].trim(), parts[1].trim()};
    }

    private static int clampSize(int size) {
        if (size < 1) {
            return 1;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
