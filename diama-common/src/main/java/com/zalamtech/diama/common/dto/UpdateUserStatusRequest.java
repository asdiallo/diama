package com.zalamtech.diama.common.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** @param status {@code ACTIVE} | {@code SUSPENDED} | {@code CLOSED} */
public record UpdateUserStatusRequest(
        @NotNull
        @Pattern(regexp = "^(ACTIVE|SUSPENDED|CLOSED)$", message = "statut invalide")
        String status
) {
}
