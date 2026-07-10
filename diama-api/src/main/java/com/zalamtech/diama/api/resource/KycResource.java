package com.zalamtech.diama.api.resource;

import com.zalamtech.diama.common.dto.KycInitDto;
import com.zalamtech.diama.common.dto.KycStatusDto;
import com.zalamtech.diama.domain.kyc.KycService;
import com.zalamtech.diama.domain.user.User;
import com.zalamtech.diama.domain.user.UserSyncService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

/**
 * Parcours KYC de l'utilisateur connecté (rôle {@code user}).
 *
 * <ul>
 *   <li>{@code POST /init}   — initialise (ou reprend) le parcours, renvoie le jeton SDK Sumsub</li>
 *   <li>{@code GET  /status} — statut et niveau KYC courants</li>
 * </ul>
 */
@Path("/api/v1/kyc")
@RolesAllowed("user")
@Produces(MediaType.APPLICATION_JSON)
public class KycResource {

    @Inject
    KycService kycService;

    @Inject
    UserSyncService userSync;

    @Inject
    JsonWebToken jwt;

    @POST
    @Path("/init")
    public KycInitDto init() {
        return kycService.initKyc(currentUserId());
    }

    @GET
    @Path("/status")
    public KycStatusDto status() {
        return kycService.getKycStatus(currentUserId());
    }

    /** Résout le {@code sub} du JWT en PK interne {@code users.id}. */
    private UUID currentUserId() {
        final User user = userSync.getOrCreateUser(jwt);
        return user.getId();
    }
}
