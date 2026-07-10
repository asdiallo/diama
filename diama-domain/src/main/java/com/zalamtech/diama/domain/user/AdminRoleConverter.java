package com.zalamtech.diama.domain.user;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Convertit {@link AdminRole} ↔ label PostgreSQL ({@code kyc-agent}, …). */
@Converter
public class AdminRoleConverter implements AttributeConverter<AdminRole, String> {

    @Override
    public String convertToDatabaseColumn(AdminRole attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public AdminRole convertToEntityAttribute(String dbData) {
        return dbData == null ? null : AdminRole.fromDbValue(dbData);
    }
}
