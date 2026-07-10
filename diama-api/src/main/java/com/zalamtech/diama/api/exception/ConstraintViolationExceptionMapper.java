package com.zalamtech.diama.api.exception;

import com.zalamtech.diama.common.exception.ApiError;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;

/**
 * Convertit les échecs de Bean Validation ({@code @Valid} sur les DTOs) en
 * réponse {@code 400} {@link ApiError}, en détaillant chaque champ fautif.
 *
 * <p>Remplace le mapper par défaut de Quarkus pour un format d'erreur cohérent
 * avec {@code BusinessExceptionMapper} (même enveloppe {@link ApiError}).</p>
 */
@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException e) {
        final List<ApiError.Violation> violations = e.getConstraintViolations().stream()
                .map(ConstraintViolationExceptionMapper::toViolation)
                .toList();

        final ApiError body = ApiError.of(
                "VALIDATION_ERROR",
                "Un ou plusieurs champs sont invalides",
                violations);

        return Response.status(Response.Status.BAD_REQUEST).entity(body).build();
    }

    private static ApiError.Violation toViolation(ConstraintViolation<?> cv) {
        // On ne garde que le dernier segment du chemin (nom du champ), pas
        // « updateMe.dto.phone ».
        String field = null;
        for (Path.Node node : cv.getPropertyPath()) {
            field = node.getName();
        }
        return new ApiError.Violation(field, cv.getMessage());
    }
}
