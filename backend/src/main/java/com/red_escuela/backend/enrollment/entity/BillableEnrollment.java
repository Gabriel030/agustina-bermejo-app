package com.red_escuela.backend.enrollment.entity;
import com.red_escuela.backend.shared.time.ApplicationTime;
import com.red_escuela.backend.identity.entity.Student;

import java.time.LocalDateTime;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;

public interface BillableEnrollment {
    Student getStudent();

    EnrollmentPaymentStatus getPaymentStatus();

    void setPaymentStatus(EnrollmentPaymentStatus standing);

    LocalDateTime getPaymentStatusUpdatedAt();

    void setPaymentStatusUpdatedAt(LocalDateTime updatedAt);

    default boolean settleArrears() {
        EnrollmentPaymentStatus standing = getPaymentStatus();
        if (standing != EnrollmentPaymentStatus.OVERDUE && standing != EnrollmentPaymentStatus.SUSPENDED) {
            return false;
        }
        setPaymentStatus(EnrollmentPaymentStatus.UP_TO_DATE);
        setPaymentStatusUpdatedAt(ApplicationTime.now());
        return true;
    }
}
