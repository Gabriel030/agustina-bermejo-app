package com.red_escuela.backend.billing.dto;

import java.time.LocalDateTime;

public record EnrollmentPaymentStatusResponse(String enrollmentType, Long enrollmentId, String paymentStatus, LocalDateTime paymentStatusUpdatedAt) {
}
