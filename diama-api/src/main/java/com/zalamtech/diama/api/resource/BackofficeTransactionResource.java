package com.zalamtech.diama.api.resource;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/** Supervision des transactions backoffice (rôles {@code ops-agent}, {@code admin}). */
@Path("/api/backoffice/transactions")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"ops-agent", "admin"})
public class BackofficeTransactionResource {

    @GET
    public List<String> list() {
        // Squelette : à brancher sur TransactionRepository.
        return List.of();
    }
}
