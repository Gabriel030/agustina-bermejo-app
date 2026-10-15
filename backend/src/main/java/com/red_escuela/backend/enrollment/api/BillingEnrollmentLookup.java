package com.red_escuela.backend.enrollment.api;

import java.util.Optional;

public interface BillingEnrollmentLookup {
    Optional<BillingEnrollmentFacts> findProgramEnrollmentFacts(Long programEnrollmentId);

    Optional<BillingEnrollmentFacts> findCourseEnrollmentFacts(Long courseEnrollmentId);
}
