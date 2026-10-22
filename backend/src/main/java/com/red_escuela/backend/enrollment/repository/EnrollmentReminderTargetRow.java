package com.red_escuela.backend.enrollment.repository;

import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;

public interface EnrollmentReminderTargetRow {
    Long getEnrollmentId();

    Long getTargetId();

    EnrollmentPaymentStatus getPaymentStatus();
}
