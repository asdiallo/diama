package com.zalamtech.diama.common.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload de {@code POST /api/v1/auth/resend-verification} (endpoint public). */
public record ResendVerificationDto(
        @NotBlank
        @Email
        @Size(max = 255)
        String email
) {
}
