package com.red_escuela.backend.enrollment.api;

import java.util.Collection;
import java.util.Set;

public interface EnrollmentBillingStandingPort {

    boolean isBillingStoppedForProgramEnrollment(Long programEnrollmentId);

    Set<Long> findSuspendedProgramEnrollmentIds(Collection<Long> programEnrollmentIds);

    Set<Long> findSuspendedCourseEnrollmentIds(Collection<Long> courseEnrollmentIds);
}
