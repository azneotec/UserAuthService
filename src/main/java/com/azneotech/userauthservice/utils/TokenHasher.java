package com.azneotech.userauthservice.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hashes bearer credentials (JWTs, password-reset tokens) before they are stored, so a database
 * dump can't be replayed. Lookups hash the presented value and compare against the stored hash.
 */
public final class TokenHasher {

    private TokenHasher() {
    }

    /** Lower-case hex SHA-256 of the UTF-8 bytes of {@code value}; always 64 characters. */
    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JVM spec but was not found", e);
        }
    }

}
