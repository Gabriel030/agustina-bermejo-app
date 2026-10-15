package com.red_escuela.backend.enrollment.policy;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public record WithdrawalDateContext(LocalDate effectiveDate, LocalDate today, Optional<LocalDate> enrollmentDate) {

    public WithdrawalDateContext {
        Objects.requireNonNull(effectiveDate, "effectiveDate");
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(enrollmentDate, "enrollmentDate");
    }
}
