package com.red_escuela.backend.enrollment.api;

import java.util.Optional;

public interface WithdrawalFactsLookup {
    Optional<WithdrawnEnrollmentFacts> findVoluntaryWithdrawalOfProgramEnrollment(Long programEnrollmentId);

    Optional<WithdrawnEnrollmentFacts> findVoluntaryWithdrawalOfCourseEnrollment(Long courseEnrollmentId);
}
