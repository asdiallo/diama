package com.zalamtech.diama.api.resource;

import com.zalamtech.diama.api.security.SecurityContext;
import com.zalamtech.diama.api.util.SortParam;
import com.zalamtech.diama.common.dto.AdminTransactionDto;
import com.zalamtech.diama.common.dto.AdminUserDetailDto;
import com.zalamtech.diama.common.dto.AdminUserListItemDto;
import com.zalamtech.diama.common.dto.Page;
import com.zalamtech.diama.common.dto.UpdateUserStatusRequest;
import com.zalamtech.diama.domain.user.AdminUserService;
import com.zalamtech.diama.domain.user.UserSession;
import com.zalamtech.diama.domain.user.UserStatus;
import io.quarkus.panache.common.Sort;
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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Administration des comptes utilisateurs (rôles {@code kyc-agent}, {@code ops-agent}, {@code admin}).
 *
 * <ul>
 *   <li>{@code GET  /users}                                  — liste paginée, filtrable (status, q)</li>
 *   <li>{@code GET  /users/{id}}                              — profil complet (user + KYC + transferts récents)</li>
 *   <li>{@code PUT  /users/{id}/status}                       — {@code ACTIVE} | {@code SUSPENDED} | {@code CLOSED}</li>
 *   <li>{@code GET  /users/{id}/transactions}                 — historique complet, paginé</li>
 *   <li>{@code GET  /users/{id}/sessions}                     — sessions Keycloak actives</li>
 *   <li>{@code POST /users/{id}/sessions/{sessionId}/revoke}  — révocation de session</li>
 * </ul>
 */
@Path("/api/v1/admin/users")
@RolesAllowed({"kyc-agent", "ops-agent", "admin"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminUserResource {

    private static final int MAX_PAGE_SIZE = 100;

    @Inject
    AdminUserService adminUsers;

    @Inject
    SecurityContext security;

    @GET
    public Page<AdminUserListItemDto> list(@QueryParam("status") String status,
                                           @QueryParam("q") String q,
                                           @QueryParam("page") @DefaultValue("0") int page,
                                           @QueryParam("size") @DefaultValue("20") int size,
                                           @QueryParam("sort") String sort) {
        final UserStatus statusFilter = parseStatus(status);
        final int clampedPage = Math.max(0, page);
        final int clampedSize = clampSize(size);
        final Sort sortSpec = SortParam.parse(sort, "createdAt");

        final List<AdminUserListItemDto> content = adminUsers.search(statusFilter, q, sortSpec, clampedPage, clampedSize);
        final long total = adminUsers.count(statusFilter, q);
        return Page.of(content, clampedPage, clampedSize, total);
    }

    @GET
    @Path("/{id}")
    public AdminUserDetailDto get(@PathParam("id") UUID id) {
        return adminUsers.getDetail(id);
    }

    @PUT
    @Path("/{id}/status")
    public Response updateStatus(@PathParam("id") UUID id, @Valid UpdateUserStatusRequest request) {
        adminUsers.updateStatus(id, UserStatus.valueOf(request.status()), security.getCurrentAdminId());
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/transactions")
    public Page<AdminTransactionDto> transactions(@PathParam("id") UUID id,
                                                   @QueryParam("page") @DefaultValue("0") int page,
                                                   @QueryParam("size") @DefaultValue("20") int size) {
        final int clampedPage = Math.max(0, page);
        final int clampedSize = clampSize(size);
        final List<AdminTransactionDto> content = adminUsers.listTransactions(id, clampedPage, clampedSize);
        final long total = adminUsers.countTransactions(id);
        return Page.of(content, clampedPage, clampedSize, total);
    }

    @GET
    @Path("/{id}/sessions")
    public List<SessionDto> sessions(@PathParam("id") UUID id) {
        return adminUsers.listSessions(id).stream().map(AdminUserResource::toSessionDto).toList();
    }

    @POST
    @Path("/{id}/sessions/{sessionId}/revoke")
    public Response revokeSession(@PathParam("id") UUID id, @PathParam("sessionId") String sessionId) {
        adminUsers.revokeSession(id, sessionId, security.getCurrentAdminId());
        return Response.noContent().build();
    }

    private static UserStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return UserStatus.valueOf(status.toUpperCase(java.util.Locale.ROOT));
    }

    private static int clampSize(int size) {
        if (size < 1) {
            return 1;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static SessionDto toSessionDto(UserSession s) {
        return new SessionDto(s.getId(), s.getKeycloakSessionId(), s.getClient() != null ? s.getClient().name() : null,
                s.getIpAddress(), s.getStartedAt(), s.getLastSeenAt());
    }

    /** Session Keycloak active d'un utilisateur donné. */
    public record SessionDto(UUID id, String keycloakSessionId, String client, String ipAddress,
                             OffsetDateTime startedAt, OffsetDateTime lastSeenAt) {
    }
}
