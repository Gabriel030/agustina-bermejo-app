package com.red_escuela.backend.billing.collections.dto;

import com.red_escuela.backend.billing.api.EnrollmentPaymentStandingView;

import java.time.LocalDate;

public record EnrollmentPaymentStandingResponse(Long enrollmentId, Long studentId, String status, LocalDate suspendedSince) {

    public static EnrollmentPaymentStandingResponse from(EnrollmentPaymentStandingView view) {
        return new EnrollmentPaymentStandingResponse(view.enrollmentId(), view.studentId(), view.status().name(), view.suspendedSince());
    }
}
