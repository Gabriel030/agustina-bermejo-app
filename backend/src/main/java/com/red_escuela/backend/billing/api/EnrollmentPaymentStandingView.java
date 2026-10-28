package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;

import java.time.LocalDate;

public record EnrollmentPaymentStandingView(Long enrollmentId, Long studentId, EnrollmentPaymentStatus status, LocalDate suspendedSince) {
}
