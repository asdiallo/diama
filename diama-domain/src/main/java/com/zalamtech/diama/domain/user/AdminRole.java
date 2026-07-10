package com.zalamtech.diama.domain.user;

/**
 * Type PostgreSQL {@code admin_role}.
 *
 * <p>Labels en base ({@code kyc-agent}, {@code ops-agent}, {@code admin},
 * {@code super-admin}) mappés via {@link AdminRoleConverter}.</p>
 */
public enum AdminRole {
    KYC_AGENT("kyc-agent"),
    OPS_AGENT("ops-agent"),
    ADMIN("admin"),
    SUPER_ADMIN("super-admin");

    private final String dbValue;

    AdminRole(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static AdminRole fromDbValue(String value) {
        for (AdminRole r : values()) {
            if (r.dbValue.equals(value)) {
                return r;
            }
        }
        throw new IllegalArgumentException("Valeur admin_role inconnue: " + value);
    }
}
