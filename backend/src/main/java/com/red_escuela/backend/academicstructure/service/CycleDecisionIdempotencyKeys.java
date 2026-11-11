package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.exception.CycleDecisionIdempotencyKeyInvalidException;

public final class CycleDecisionIdempotencyKeys {

    private static final int MAX_LENGTH = 100;

    private CycleDecisionIdempotencyKeys() {
    }

    public static String normalize(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }
        var key = rawKey.trim();
        if (key.length() > MAX_LENGTH) {
            throw new CycleDecisionIdempotencyKeyInvalidException();
        }
        return key;
    }
}
