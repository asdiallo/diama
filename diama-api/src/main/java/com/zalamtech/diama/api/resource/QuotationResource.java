package com.zalamtech.diama.api.resource;

import com.zalamtech.diama.common.dto.QuotationDto;
import com.zalamtech.diama.common.dto.QuotationRequest;
import com.zalamtech.diama.domain.quotation.QuotationService;
import com.zalamtech.diama.domain.user.User;
import com.zalamtech.diama.domain.user.UserSyncService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

/**
 * Émission de devis de transfert pour l'utilisateur connecté (rôle {@code user}).
 * Le taux renvoyé est verrouillé 15 minutes.
 */
@Path("/api/v1/quotations")
@RolesAllowed("user")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class QuotationResource {

    @Inject
    QuotationService quotations;

    @Inject
    UserSyncService userSync;

    @Inject
    JsonWebToken jwt;

    /** POST / — construit un devis (taux verrouillé 15 min). */
    @POST
    public QuotationDto quote(@Valid QuotationRequest request) {
        return quotations.buildQuotation(
                currentUserId(),
                request.sourceAmount(),
                request.sourceCurrency(),
                request.payerId());
    }

    /** Résout le {@code sub} du JWT en PK interne {@code users.id}. */
    private UUID currentUserId() {
        final User user = userSync.getOrCreateUser(jwt);
        return user.getId();
    }
}
