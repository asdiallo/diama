package com.zalamtech.diama.api.resource;

import com.zalamtech.diama.api.security.SecurityContext;
import com.zalamtech.diama.domain.kyc.KycProfile;
import com.zalamtech.diama.domain.kyc.KycProfileRepository;
import com.zalamtech.diama.domain.kyc.KycVerificationStatus;
import com.zalamtech.diama.domain.user.User;
import com.zalamtech.diama.domain.user.UserStatus;
import com.zalamtech.diama.domain.user.UserSyncService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

/** Endpoints de l'application mobile (rôle {@code user}). */
@Path("/api/mobile/me")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
@Tag(name = "users")
@SecurityRequirement(name = "bearerAuth")
public class MobileAccountResource {

    @Inject
    SecurityContext security;

    @Inject
    JsonWebToken jwt;

    @Inject
    UserSyncService userSync;

    @Inject
    KycProfileRepository kycProfiles;

    /** Profil de l'utilisateur courant, synchronisé depuis Keycloak. */
    public record MeResponse(
            UUID userId,
            String keycloakId,
            String email,
            String phone,
            String firstName,
            String lastName,
            UserStatus status,
            int kycLevel,
            KycVerificationStatus kycStatus,
            String sessionId,
            String client
    ) {
    }

    @Operation(summary = "Compte mobile courant (profil + KYC + session)")
    @GET
    @Transactional
    public MeResponse me() {
        final User user = userSync.getOrCreateUser(jwt);
        final KycProfile kyc = kycProfiles.findByUserId(user.getId()).orElse(null);

        return new MeResponse(
                user.getId(),
                user.getKeycloakId(),
                user.getEmail(),
                user.getPhone(),
                user.getFirstName(),
                user.getLastName(),
                user.getStatus(),
                security.getKycLevel(),
                kyc != null ? kyc.getVerificationStatus() : null,
                security.getKeycloakSessionId(),
                security.getClientId());
    }
}
