package com.zalamtech.diama.api.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

/** Configuration système (rôle {@code admin} uniquement). */
@Path("/api/admin/config")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class AdminConfigResource {

    @GET
    public Map<String, String> config() {
        // Squelette : à brancher sur system_config.
        return Map.of();
    }
}
