package com.zalamtech.diama.api.resource;

import com.zalamtech.diama.common.dto.CreateTransferRequest;
import com.zalamtech.diama.common.dto.Page;
import com.zalamtech.diama.common.dto.TransferDto;
import com.zalamtech.diama.domain.transaction.TransferService;
import com.zalamtech.diama.domain.user.User;
import com.zalamtech.diama.domain.user.UserSyncService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

/**
 * Gestion des transferts de l'utilisateur connecté (rôle {@code user}).
 *
 * <ul>
 *   <li>{@code POST /}       — initie un transfert à partir d'un devis verrouillé</li>
 *   <li>{@code GET /}        — historique paginé des transferts</li>
 *   <li>{@code GET /{id}}    — détail d'un transfert</li>
 *   <li>{@code DELETE /{id}} — annulation (uniquement si statut {@code INITIATED})</li>
 * </ul>
 */
@Path("/api/v1/transfers")
@RolesAllowed("user")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "transfers")
@SecurityRequirement(name = "bearerAuth")
public class TransferResource {

    private static final int MAX_PAGE_SIZE = 100;

    @Inject
    TransferService transfers;

    @Inject
    UserSyncService userSync;

    @Inject
    JsonWebToken jwt;

    /** POST / — initie un transfert. Renvoie {@code 201 Created} avec le transfert. */
    @Operation(summary = "Initiation d'un transfert depuis un devis")
    @POST
    public Response initiate(@Valid CreateTransferRequest request) {
        final TransferDto created = transfers.initiateTransfer(currentUserId(), request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    /** GET / — historique paginé, les plus récents d'abord. */
    @Operation(summary = "Historique paginé des transferts")
    @GET
    public Page<TransferDto> list(@QueryParam("page") @DefaultValue("0") int page,
                                  @QueryParam("size") @DefaultValue("20") int size) {
        return transfers.listTransfers(currentUserId(), Math.max(0, page), clampSize(size));
    }

    /** GET /{id} — détail d'un transfert appartenant à l'utilisateur. */
    @Operation(summary = "Détail d'un transfert")
    @GET
    @Path("/{id}")
    public TransferDto get(@PathParam("id") UUID id) {
        return transfers.getTransfer(id, currentUserId());
    }

    /** DELETE /{id} — annule un transfert encore au statut {@code INITIATED}. */
    @Operation(summary = "Annulation d'un transfert INITIATED")
    @DELETE
    @Path("/{id}")
    public Response cancel(@PathParam("id") UUID id) {
        transfers.cancelTransfer(id, currentUserId());
        return Response.noContent().build();
    }

    private static int clampSize(int size) {
        if (size < 1) {
            return 1;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /** Résout le {@code sub} du JWT en PK interne {@code users.id}. */
    private UUID currentUserId() {
        final User user = userSync.getOrCreateUser(jwt);
        return user.getId();
    }
}
