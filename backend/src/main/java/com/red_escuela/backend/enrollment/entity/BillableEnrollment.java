package com.red_escuela.backend.enrollment.entity;
import com.red_escuela.backend.shared.time.ApplicationTime;
import com.red_escuela.backend.identity.entity.Student;

import java.time.LocalDateTime;
import com.red_escuela.backend.billing.entity.EnrollmentBillingStanding;

public interface BillableEnrollment {
    Student getStudent();

    EnrollmentBillingStanding getBillingStanding();

    void setBillingStanding(EnrollmentBillingStanding standing);

    LocalDateTime getBillingStandingUpdatedAt();

    void setBillingStandingUpdatedAt(LocalDateTime updatedAt);

    default boolean settleArrears() {
        EnrollmentBillingStanding standing = getBillingStanding();
        if (standing != EnrollmentBillingStanding.OVERDUE && standing != EnrollmentBillingStanding.SUSPENDED) {
            return false;
        }
        setBillingStanding(EnrollmentBillingStanding.UP_TO_DATE);
        setBillingStandingUpdatedAt(ApplicationTime.now());
        return true;
    }
}
