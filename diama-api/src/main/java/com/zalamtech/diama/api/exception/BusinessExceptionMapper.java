package com.zalamtech.diama.api.exception;

import com.zalamtech.diama.common.exception.ApiError;
import com.zalamtech.diama.common.exception.BusinessException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * Convertit toute {@link BusinessException} en réponse JSON {@link ApiError},
 * avec le statut HTTP porté par l'exception ({@code 404}, {@code 403},
 * {@code 422}, …). Couvre toutes les sous-classes métier.
 */
@Slf4j
@Provider
public class BusinessExceptionMapper implements ExceptionMapper<BusinessException> {

    @Override
    public Response toResponse(BusinessException e) {
        log.debug("Erreur métier {} ({}) : {}", e.getErrorCode(), e.getHttpStatus(), e.getMessage());
        return Response.status(e.getHttpStatus())
                .entity(ApiError.of(e.getErrorCode(), e.getMessage()))
                .build();
    }
}
