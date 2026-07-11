package com.zalamtech.diama.common.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Ligne de la liste backoffice des utilisateurs. */
public record AdminUserListItemDto(
        UUID id,
        String email,
        String phone,
        String firstName,
        String lastName,
        String status,
        String countryOfResidence,
        OffsetDateTime createdAt
) {
}
