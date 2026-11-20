package com.red_escuela.backend.enrollment.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

final class WaitlistSectionExpiry {

    private WaitlistSectionExpiry() {
    }

    static LocalDate of(LocalDateTime enrollmentEnd) {
        return enrollmentEnd == null ? null : enrollmentEnd.toLocalDate();
    }
}
