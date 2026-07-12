package com.zalamtech.diama.api.resource;

import com.zalamtech.diama.api.security.SecurityContext;
import com.zalamtech.diama.common.dto.AdminFeeDto;
import com.zalamtech.diama.common.dto.CreateFeeTierRequest;
import com.zalamtech.diama.domain.fee.AdminFeeService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

/**
 * Administration de la grille tarifaire (rôle {@code admin}).
 *
 * <ul>
 *   <li>{@code GET    /fees}      — grille active</li>
 *   <li>{@code POST   /fees}      — créer un palier</li>
 *   <li>{@code PUT    /fees/{id}} — remplacer un palier ({@code valid_until} posé sur l'ancien)</li>
 *   <li>{@code DELETE /fees/{id}} — désactiver un palier</li>
 * </ul>
 */
@Path("/api/v1/admin/fees")
@RolesAllowed("admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "admin")
@SecurityRequirement(name = "bearerAuth")
public class AdminFeeResource {

    @Inject
    AdminFeeService adminFees;

    @Inject
    SecurityContext security;

    @Operation(summary = "Grille tarifaire active")
    @GET
    public List<AdminFeeDto> list() {
        return adminFees.listActive();
    }

    @Operation(summary = "Création d'un palier de frais")
    @POST
    public Response create(@Valid CreateFeeTierRequest request) {
        final AdminFeeDto created = adminFees.create(request, security.getCurrentAdminId());
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @Operation(summary = "Remplacement d'un palier (versionné)")
    @PUT
    @Path("/{id}")
    public AdminFeeDto update(@PathParam("id") UUID id, @Valid CreateFeeTierRequest request) {
        return adminFees.update(id, request, security.getCurrentAdminId());
    }

    @Operation(summary = "Désactivation d'un palier")
    @DELETE
    @Path("/{id}")
    public Response deactivate(@PathParam("id") UUID id) {
        adminFees.deactivate(id, security.getCurrentAdminId());
        return Response.noContent().build();
    }
}
