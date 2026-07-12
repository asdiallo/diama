package com.zalamtech.diama.api.resource;

import com.zalamtech.diama.common.dto.CorridorStatsDto;
import com.zalamtech.diama.common.dto.DashboardSummaryDto;
import com.zalamtech.diama.common.dto.HourlyVolumeDto;
import com.zalamtech.diama.domain.dashboard.AdminDashboardService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

/**
 * Tableau de bord backoffice (rôles {@code ops-agent}, {@code admin}).
 *
 * <ul>
 *   <li>{@code GET /dashboard/summary}   — synthèse du jour (activité, files d'attente)</li>
 *   <li>{@code GET /dashboard/corridors} — statistiques par corridor (30 jours glissants)</li>
 *   <li>{@code GET /dashboard/hourly}    — volume horaire des 24 dernières heures</li>
 * </ul>
 */
@Path("/api/v1/admin/dashboard")
@RolesAllowed({"ops-agent", "admin"})
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "admin")
@SecurityRequirement(name = "bearerAuth")
public class AdminDashboardResource {

    @Inject
    AdminDashboardService dashboard;

    @Operation(summary = "Synthèse du jour (activité, files d'attente)")
    @GET
    @Path("/summary")
    public DashboardSummaryDto summary() {
        return dashboard.summary();
    }

    @Operation(summary = "Statistiques par corridor (30 jours)")
    @GET
    @Path("/corridors")
    public List<CorridorStatsDto> corridors() {
        return dashboard.corridors();
    }

    @Operation(summary = "Volume horaire des 24 dernières heures")
    @GET
    @Path("/hourly")
    public List<HourlyVolumeDto> hourly() {
        return dashboard.hourly();
    }
}
