package com.zalamtech.diama.api.resource;

import com.zalamtech.diama.api.security.SecurityContext;
import com.zalamtech.diama.api.util.SortParam;
import com.zalamtech.diama.common.dto.AdminTransactionDto;
import com.zalamtech.diama.common.dto.ForceStatusRequest;
import com.zalamtech.diama.common.dto.Page;
import com.zalamtech.diama.common.dto.RfiPendingDto;
import com.zalamtech.diama.common.dto.SubmitRfiDocsRequest;
import com.zalamtech.diama.common.dto.TransactionDetailDto;
import com.zalamtech.diama.domain.transaction.AdminTransactionService;
import com.zalamtech.diama.domain.transaction.RFIDocument;
import com.zalamtech.diama.domain.transaction.TxStatus;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

/**
 * Administration des transactions (rôles {@code ops-agent}, {@code admin}).
 *
 * <ul>
 *   <li>{@code GET  /transactions}                          — liste paginée, filtrable</li>
 *   <li>{@code GET  /transactions/{id}}                      — détail + historique FSM</li>
 *   <li>{@code POST /transactions/{id}/force-status}         — transition manuelle (motif obligatoire)</li>
 *   <li>{@code GET  /transactions/rfi-pending}                — RFI Thunes en attente, toutes transactions</li>
 *   <li>{@code POST /transactions/{id}/rfi/{rfiId}/submit}    — soumission des documents/infos RFI</li>
 * </ul>
 */
@Path("/api/v1/admin/transactions")
@RolesAllowed({"ops-agent", "admin"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "admin")
@SecurityRequirement(name = "bearerAuth")
public class AdminTransactionResource {

    private static final int MAX_PAGE_SIZE = 100;

    @Inject
    AdminTransactionService adminTransactions;

    @Inject
    SecurityContext security;

    @Operation(summary = "Recherche paginée des transactions")
    @GET
    public Page<AdminTransactionDto> list(@QueryParam("userId") UUID userId,
                                          @QueryParam("status") String status,
                                          @QueryParam("sourceCurrency") String sourceCurrency,
                                          @QueryParam("destCurrency") String destCurrency,
                                          @QueryParam("from") String fromIso,
                                          @QueryParam("to") String toIso,
                                          @QueryParam("page") @DefaultValue("0") int page,
                                          @QueryParam("size") @DefaultValue("20") int size,
                                          @QueryParam("sort") String sort) {
        final TxStatus statusFilter = status != null && !status.isBlank() ? TxStatus.valueOf(status) : null;
        final OffsetDateTime from = parseInstant(fromIso);
        final OffsetDateTime to = parseInstant(toIso);
        final int clampedPage = Math.max(0, page);
        final int clampedSize = clampSize(size);
        final Sort sortSpec = SortParam.parse(sort, "createdAt");

        final List<AdminTransactionDto> content = adminTransactions.search(
                userId, statusFilter, sourceCurrency, destCurrency, from, to, sortSpec, clampedPage, clampedSize);
        final long total = adminTransactions.count(userId, statusFilter, sourceCurrency, destCurrency, from, to);
        return Page.of(content, clampedPage, clampedSize, total);
    }

    @Operation(summary = "Détail + historique FSM d'une transaction")
    @GET
    @Path("/{id}")
    public TransactionDetailDto get(@PathParam("id") UUID id) {
        return adminTransactions.getDetail(id);
    }

    @Operation(summary = "Transition manuelle de statut (motif obligatoire)")
    @POST
    @Path("/{id}/force-status")
    public Response forceStatus(@PathParam("id") UUID id, @Valid ForceStatusRequest request) {
        adminTransactions.forceStatus(id, TxStatus.valueOf(request.status()), request.reason(), security.getCurrentAdminId());
        return Response.noContent().build();
    }

    @Operation(summary = "RFI Thunes en attente de réponse")
    @GET
    @Path("/rfi-pending")
    public List<RfiPendingDto> rfiPending() {
        return adminTransactions.listPendingRfi();
    }

    @Operation(summary = "Soumission des documents/infos d'une RFI")
    @POST
    @Path("/{id}/rfi/{rfiId}/submit")
    public Response submitRfi(@PathParam("id") UUID id, @PathParam("rfiId") UUID rfiId,
                              @Valid SubmitRfiDocsRequest request) {
        final List<RFIDocument> docs = request.documents() == null ? List.of() : request.documents().stream()
                .map(d -> new RFIDocument(d.docType(), d.mimeType(), Base64.getDecoder().decode(d.contentBase64())))
                .toList();
        final Map<String, String> info = request.info() != null ? request.info() : Map.of();

        adminTransactions.submitRfiDocs(id, rfiId, docs, info, security.getCurrentAdminId());
        return Response.noContent().build();
    }

    private static OffsetDateTime parseInstant(String iso) {
        return iso != null && !iso.isBlank() ? OffsetDateTime.parse(iso) : null;
    }

    private static int clampSize(int size) {
        if (size < 1) {
            return 1;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
