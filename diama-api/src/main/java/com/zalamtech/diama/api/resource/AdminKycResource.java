package com.zalamtech.diama.api.resource;

import com.zalamtech.diama.api.security.SecurityContext;
import com.zalamtech.diama.common.dto.AdminKycReviewDto;
import com.zalamtech.diama.common.exception.ResourceNotFoundException;
import com.zalamtech.diama.domain.kyc.KycProfile;
import com.zalamtech.diama.domain.kyc.KycProfileRepository;
import com.zalamtech.diama.domain.kyc.KycService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

/**
 * Revue KYC backoffice (rôle {@code kyc-agent}).
 *
 * <ul>
 *   <li>{@code GET  /pending}          — dossiers en attente de revue manuelle</li>
 *   <li>{@code GET  /{userId}}         — dossier KYC complet (documents resynchronisés depuis Sumsub)</li>
 *   <li>{@code POST /{userId}/approve} — validation manuelle</li>
 *   <li>{@code POST /{userId}/reject}  — rejet avec motif</li>
 * </ul>
 */
@Path("/api/v1/admin/kyc")
@RolesAllowed("kyc-agent")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminKycResource {

    @Inject
    KycService kycService;

    @Inject
    KycProfileRepository kycProfiles;

    @Inject
    SecurityContext security;

    @GET
    @Path("/pending")
    public List<AdminKycReviewDto> pending() {
        return kycService.getPendingReview();
    }

    @GET
    @Path("/{userId}")
    public AdminKycReviewDto get(@PathParam("userId") UUID userId) {
        return kycService.getFullDossier(userId);
    }

    @POST
    @Path("/{userId}/approve")
    public Response approve(@PathParam("userId") UUID userId) {
        kycService.manualReview(resolveKycProfileId(userId), true, null, security.getCurrentAdminId());
        return Response.noContent().build();
    }

    @POST
    @Path("/{userId}/reject")
    public Response reject(@PathParam("userId") UUID userId, @Valid RejectRequest request) {
        kycService.manualReview(resolveKycProfileId(userId), false, request.reason(), security.getCurrentAdminId());
        return Response.noContent().build();
    }

    private UUID resolveKycProfileId(UUID userId) {
        return kycProfiles.findByUserId(userId)
                .map(KycProfile::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Profil KYC", userId));
    }

    /** @param reason motif du rejet, communiqué à l'utilisateur */
    public record RejectRequest(@NotBlank String reason) {
    }
}
