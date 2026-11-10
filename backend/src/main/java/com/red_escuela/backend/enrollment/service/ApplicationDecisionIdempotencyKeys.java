package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.exception.ApplicationDecisionIdempotencyKeyInvalidException;

public final class ApplicationDecisionIdempotencyKeys {

    private static final int MAX_LENGTH = 100;

    private ApplicationDecisionIdempotencyKeys() {
    }

    public static String normalize(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }
        String key = rawKey.trim();
        if (key.length() > MAX_LENGTH) {
            throw new ApplicationDecisionIdempotencyKeyInvalidException();
        }
        return key;
    }
}
