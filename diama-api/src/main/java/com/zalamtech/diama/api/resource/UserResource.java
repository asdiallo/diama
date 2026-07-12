package com.zalamtech.diama.api.resource;

import com.zalamtech.diama.common.dto.BeneficiaryDto;
import com.zalamtech.diama.common.dto.CreateBeneficiaryDto;
import com.zalamtech.diama.common.dto.DeviceRegistrationDto;
import com.zalamtech.diama.common.dto.LimitCheckResult;
import com.zalamtech.diama.common.dto.UserProfileDto;
import com.zalamtech.diama.common.dto.UserUpdateDto;
import com.zalamtech.diama.domain.user.User;
import com.zalamtech.diama.domain.user.UserService;
import com.zalamtech.diama.domain.user.UserSyncService;
import io.quarkus.security.Authenticated;
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
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

/**
 * Endpoints du profil de l'utilisateur connecté (application mobile).
 *
 * <p>Toutes les routes opèrent sur {@code /me} : l'identité provient du JWT
 * Keycloak, jamais d'un identifiant fourni par le client. Le {@code sub} du JWT
 * est résolu en PK interne {@code users.id} via {@link UserSyncService}
 * (création à la première connexion).</p>
 */
@Path("/api/v1/users")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "users")
@SecurityRequirement(name = "bearerAuth")
public class UserResource {

    @Inject
    UserService userService;

    @Inject
    UserSyncService userSync;

    @Inject
    JsonWebToken jwt;

    /** GET /me — profil de l'utilisateur connecté. */
    @Operation(summary = "Profil de l'utilisateur connecté")
    @GET
    @Path("/me")
    public UserProfileDto me() {
        return userService.getProfile(currentUserId());
    }

    /** PUT /me — mise à jour partielle du profil. */
    @Operation(summary = "Mise à jour partielle du profil")
    @PUT
    @Path("/me")
    public UserProfileDto updateMe(@Valid UserUpdateDto dto) {
        return userService.updateProfile(currentUserId(), dto);
    }

    /** GET /me/beneficiaries — bénéficiaires actifs. */
    @Operation(summary = "Bénéficiaires actifs")
    @GET
    @Path("/me/beneficiaries")
    public List<BeneficiaryDto> beneficiaries() {
        return userService.listBeneficiaries(currentUserId());
    }

    /** POST /me/beneficiaries — ajout d'un bénéficiaire. */
    @Operation(summary = "Ajout d'un bénéficiaire")
    @POST
    @Path("/me/beneficiaries")
    public Response addBeneficiary(@Valid CreateBeneficiaryDto dto) {
        final BeneficiaryDto created = userService.addBeneficiary(currentUserId(), dto);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    /** DELETE /me/beneficiaries/{id} — désactivation (soft delete). */
    @Operation(summary = "Désactivation d'un bénéficiaire")
    @DELETE
    @Path("/me/beneficiaries/{id}")
    public Response deactivateBeneficiary(@PathParam("id") UUID id) {
        userService.deactivateBeneficiary(currentUserId(), id);
        return Response.noContent().build();
    }

    /** POST /me/devices — enregistrement d'un token push. */
    @Operation(summary = "Enregistrement d'un appareil (push)")
    @POST
    @Path("/me/devices")
    public Response registerDevice(@Valid DeviceRegistrationDto dto) {
        userService.registerDevice(currentUserId(), dto);
        return Response.status(Response.Status.CREATED).build();
    }

    /** GET /me/limits — plafonds KYC courants (montant testé = 0). */
    @Operation(summary = "Plafonds mensuels KYC")
    @GET
    @Path("/me/limits")
    public LimitCheckResult limits() {
        return userService.checkMonthlyLimit(currentUserId(), BigDecimal.ZERO);
    }

    /** Résout le {@code sub} du JWT en PK interne {@code users.id}. */
    private UUID currentUserId() {
        final User user = userSync.getOrCreateUser(jwt);
        return user.getId();
    }
}
