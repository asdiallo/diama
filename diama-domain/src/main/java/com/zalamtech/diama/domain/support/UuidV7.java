package com.zalamtech.diama.domain.support;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Générateur d'UUID version 7 (RFC 9562) : 48 bits de timestamp Unix (ms) en
 * tête, puis version/variant et aléa. Les identifiants sont donc croissants
 * dans le temps — excellente localité d'index côté PostgreSQL.
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID generate() {
        final byte[] value = new byte[16];
        RANDOM.nextBytes(value);

        final long timestamp = System.currentTimeMillis();
        // 48 bits de timestamp (millisecondes) sur les octets 0..5
        value[0] = (byte) ((timestamp >>> 40) & 0xFF);
        value[1] = (byte) ((timestamp >>> 32) & 0xFF);
        value[2] = (byte) ((timestamp >>> 24) & 0xFF);
        value[3] = (byte) ((timestamp >>> 16) & 0xFF);
        value[4] = (byte) ((timestamp >>> 8) & 0xFF);
        value[5] = (byte) (timestamp & 0xFF);

        // version 7 (0b0111) dans le quartet haut de l'octet 6
        value[6] = (byte) ((value[6] & 0x0F) | 0x70);
        // variant RFC 4122 (0b10xx) dans les bits hauts de l'octet 8
        value[8] = (byte) ((value[8] & 0x3F) | 0x80);

        long msb = 0L;
        long lsb = 0L;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (value[i] & 0xFF);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (value[i] & 0xFF);
        }
        return new UUID(msb, lsb);
    }
}
