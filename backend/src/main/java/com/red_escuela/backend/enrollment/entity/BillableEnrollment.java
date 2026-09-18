package com.red_escuela.backend.enrollment.entity;
import com.red_escuela.backend.identity.entity.Student;

import java.time.LocalDateTime;
import com.red_escuela.backend.billing.entity.EnrollmentBillingStanding;

public interface BillableEnrollment {
    Student getStudent();

    EnrollmentBillingStanding getBillingStanding();

    void setBillingStanding(EnrollmentBillingStanding standing);

    LocalDateTime getBillingStandingUpdatedAt();

    void setBillingStandingUpdatedAt(LocalDateTime updatedAt);
}
