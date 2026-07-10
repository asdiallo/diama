package com.zalamtech.diama.domain.user;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Convertit {@link SessionClient} ↔ label PostgreSQL ({@code mobile-app}, …). */
@Converter
public class SessionClientConverter implements AttributeConverter<SessionClient, String> {

    @Override
    public String convertToDatabaseColumn(SessionClient attribute) {
        return attribute == null ? null : attribute.getDbValue();
    }

    @Override
    public SessionClient convertToEntityAttribute(String dbData) {
        return dbData == null ? null : SessionClient.fromDbValue(dbData);
    }
}
