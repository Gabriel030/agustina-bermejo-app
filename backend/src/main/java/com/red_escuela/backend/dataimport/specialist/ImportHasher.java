package com.red_escuela.backend.dataimport.specialist;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

public final class ImportHasher {
    private ImportHasher() {
    }

    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String contentHash(Map<String, String> payload) {
        StringBuilder canonical = new StringBuilder();
        new TreeMap<>(payload).forEach((column, value) -> canonical.append(column).append('=').append(value).append('\n'));
        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }
}
