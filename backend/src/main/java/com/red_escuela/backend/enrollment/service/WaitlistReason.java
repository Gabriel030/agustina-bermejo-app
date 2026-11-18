package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.exception.WaitlistReasonRequiredException;

public final class WaitlistReason {

    private static final int MIN_LENGTH = 10;
    private static final int MAX_LENGTH = 500;

    private WaitlistReason() {
    }

    public static String validated(String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.length() < MIN_LENGTH || trimmed.length() > MAX_LENGTH) {
            throw new WaitlistReasonRequiredException();
        }
        return trimmed;
    }
}
