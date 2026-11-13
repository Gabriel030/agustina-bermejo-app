package com.red_escuela.backend.graduation.certificate.regular.specialist;

import java.util.function.Supplier;

final class RegularCertificateReason {

    static final int MIN_LENGTH = 10;
    static final int MAX_LENGTH = 500;

    private RegularCertificateReason() {
    }

    static String required(String raw, Supplier<? extends RuntimeException> invalid) {
        String reason = raw == null ? "" : raw.trim();
        if (reason.length() < MIN_LENGTH || reason.length() > MAX_LENGTH) {
            throw invalid.get();
        }
        return reason;
    }
}
