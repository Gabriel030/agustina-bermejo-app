package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record EnrollmentPaymentStandingView(Long enrollmentId, Long studentId, EnrollmentPaymentStatus status, LocalDate suspendedSince,
                                            LocalDateTime statusChangedAt) {

    public static EnrollmentPaymentStatus statusOrUpToDate(EnrollmentPaymentStandingView standing) {
        return standing == null ? EnrollmentPaymentStatus.UP_TO_DATE : standing.status();
    }

    public static LocalDateTime statusChangedAtOrNull(EnrollmentPaymentStandingView standing) {
        return standing == null ? null : standing.statusChangedAt();
    }
}
