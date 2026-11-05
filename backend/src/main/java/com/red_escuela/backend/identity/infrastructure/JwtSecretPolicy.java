package com.red_escuela.backend.identity.infrastructure;

public final class JwtSecretPolicy {
    public static final int MIN_LENGTH = 32;

    private JwtSecretPolicy() {
    }

    public static String requireStrong(String secret) {
        if (secret == null || secret.length() < MIN_LENGTH) {
            throw new IllegalStateException("jwt.secret debe tener al menos " + MIN_LENGTH + " caracteres");
        }
        return secret;
    }
}
