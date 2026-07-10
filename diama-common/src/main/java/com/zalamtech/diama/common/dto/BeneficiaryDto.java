package com.zalamtech.diama.common.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Vue d'un bénéficiaire enregistré (destinataire d'un transfert). */
public record BeneficiaryDto(
        UUID id,
        String nickname,
        String firstName,
        String lastName,
        String phoneMsisdn,
        String bankAccountNo,
        String swiftBic,
        String countryIso,
        String currency,
        Long thunesPayerId,
        String payerService,
        boolean active,
        OffsetDateTime createdAt
) {
}
