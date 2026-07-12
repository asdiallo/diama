package com.zalamtech.diama.api.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

/** Configuration système (rôle {@code admin} uniquement). */
@Path("/api/admin/config")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
@Tag(name = "admin")
@SecurityRequirement(name = "bearerAuth")
public class AdminConfigResource {

    @Operation(summary = "Configuration système")
    @GET
    public Map<String, String> config() {
        // Squelette : à brancher sur system_config.
        return Map.of();
    }
}
