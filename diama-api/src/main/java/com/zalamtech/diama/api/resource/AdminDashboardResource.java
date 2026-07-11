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
public class AdminDashboardResource {

    @Inject
    AdminDashboardService dashboard;

    @GET
    @Path("/summary")
    public DashboardSummaryDto summary() {
        return dashboard.summary();
    }

    @GET
    @Path("/corridors")
    public List<CorridorStatsDto> corridors() {
        return dashboard.corridors();
    }

    @GET
    @Path("/hourly")
    public List<HourlyVolumeDto> hourly() {
        return dashboard.hourly();
    }
}
