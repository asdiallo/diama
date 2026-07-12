package com.zalamtech.diama.api.resource;

import com.zalamtech.diama.api.security.SecurityContext;
import com.zalamtech.diama.common.dto.ActiveSessionDto;
import com.zalamtech.diama.common.dto.AuditEventDto;
import com.zalamtech.diama.common.dto.Page;
import com.zalamtech.diama.domain.audit.ActiveSessionRepository;
import com.zalamtech.diama.domain.audit.AuditEvent;
import com.zalamtech.diama.domain.audit.AuditEventRepository;
import com.zalamtech.diama.domain.user.UserSessionService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

/**
 * Consultation d'audit backoffice (rôle {@code admin} uniquement).
 *
 * <ul>
 *   <li>{@code GET    /}                              — liste paginée, filtrable (userId, eventType, dates)</li>
 *   <li>{@code GET    /export}                         — export CSV en flux des 30 derniers jours</li>
 *   <li>{@code GET    /sessions}                       — sessions Keycloak actives ({@code v_active_sessions})</li>
 *   <li>{@code DELETE /sessions/{keycloakSessionId}}   — invalide une session (déconnexion forcée)</li>
 * </ul>
 */
@Path("/api/v1/admin/audit")
@RolesAllowed("admin")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "admin")
@SecurityRequirement(name = "bearerAuth")
public class AdminAuditResource {

    private static final int MAX_PAGE_SIZE = 200;
    private static final int EXPORT_WINDOW_DAYS = 30;
    private static final String CSV_HEADER =
            "occurred_at,event_type,entity_type,entity_id,user_id,admin_id,keycloak_user_id,keycloak_client,ip_address\n";

    @Inject
    AuditEventRepository auditEvents;

    @Inject
    ActiveSessionRepository activeSessions;

    @Inject
    UserSessionService userSessions;

    @Inject
    SecurityContext security;

    @Operation(summary = "Journal d'audit paginé et filtrable")
    @GET
    public Page<AuditEventDto> list(@QueryParam("userId") UUID userId,
                                    @QueryParam("eventType") String eventType,
                                    @QueryParam("from") String fromIso,
                                    @QueryParam("to") String toIso,
                                    @QueryParam("page") @DefaultValue("0") int page,
                                    @QueryParam("size") @DefaultValue("20") int size) {
        final OffsetDateTime from = parseInstant(fromIso);
        final OffsetDateTime to = parseInstant(toIso);
        final int clampedPage = Math.max(0, page);
        final int clampedSize = clampSize(size);

        final List<AuditEventDto> content = auditEvents.search(userId, eventType, from, to, clampedPage, clampedSize)
                .stream()
                .map(AdminAuditResource::toDto)
                .toList();
        final long total = auditEvents.count(userId, eventType, from, to);
        return Page.of(content, clampedPage, clampedSize, total);
    }

    /** Export CSV en flux (transaction ouverte manuellement : le curseur ne survit pas à la fin de la méthode). */
    @Operation(summary = "Export CSV des 30 derniers jours")
    @GET
    @Path("/export")
    @Produces("text/csv")
    public Response export() {
        final StreamingOutput stream = output -> QuarkusTransaction.requiringNew().run(() -> writeCsv(output));
        return Response.ok(stream)
                .header("Content-Disposition", "attachment; filename=\"audit-export.csv\"")
                .build();
    }

    @Operation(summary = "Sessions Keycloak actives")
    @GET
    @Path("/sessions")
    public List<ActiveSessionDto> sessions() {
        return activeSessions.findActive();
    }

    @Operation(summary = "Invalidation d'une session Keycloak")
    @DELETE
    @Path("/sessions/{keycloakSessionId}")
    public Response endSession(@PathParam("keycloakSessionId") String keycloakSessionId) {
        userSessions.endSession(keycloakSessionId, security.getCurrentAdminId());
        return Response.noContent().build();
    }

    // ---------------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------------

    private void writeCsv(java.io.OutputStream output) {
        try (Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            writer.write(CSV_HEADER);
            final OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(EXPORT_WINDOW_DAYS);
            try (Stream<AuditEvent> events = auditEvents.streamSince(since)) {
                events.forEach(e -> writeRow(writer, e));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeRow(Writer writer, AuditEvent event) {
        try {
            writer.write(String.join(",",
                    csv(event.getOccurredAt() != null ? event.getOccurredAt().toString() : ""),
                    csv(event.getEventType()),
                    csv(event.getEntityType()),
                    csv(event.getEntityId() != null ? event.getEntityId().toString() : ""),
                    csv(event.getUserId() != null ? event.getUserId().toString() : ""),
                    csv(event.getAdminId() != null ? event.getAdminId().toString() : ""),
                    csv(event.getKeycloakUserId()),
                    csv(event.getKeycloakClient()),
                    csv(event.getIpAddress())));
            writer.write("\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
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

    private static AuditEventDto toDto(AuditEvent e) {
        return new AuditEventDto(
                e.getId(),
                e.getUserId(),
                e.getAdminId(),
                e.getKeycloakUserId(),
                e.getKeycloakClient(),
                e.getEventType(),
                e.getEntityType(),
                e.getEntityId(),
                e.getIpAddress(),
                e.getOccurredAt());
    }
}
