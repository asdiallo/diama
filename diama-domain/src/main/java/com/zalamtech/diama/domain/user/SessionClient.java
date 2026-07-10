package com.zalamtech.diama.domain.user;

/**
 * Type PostgreSQL {@code session_client}.
 *
 * <p>Les labels en base ({@code mobile-app}, {@code backoffice-app}) ne sont pas
 * des identifiants Java valides : le mapping passe par {@link SessionClientConverter}.</p>
 */
public enum SessionClient {
    MOBILE_APP("mobile-app"),
    BACKOFFICE_APP("backoffice-app");

    private final String dbValue;

    SessionClient(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static SessionClient fromDbValue(String value) {
        for (SessionClient c : values()) {
            if (c.dbValue.equals(value)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Valeur session_client inconnue: " + value);
    }
}
