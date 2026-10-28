package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record EnrollmentPaymentStatusChangeView(String event, EnrollmentPaymentStatus fromStatus, EnrollmentPaymentStatus toStatus, String reason,
                                                Long actorPersonId, String actorProcess, LocalDate businessDate, LocalDateTime occurredAt) {
}
