package com.red_escuela.backend.billing.api;

import java.util.List;
import java.util.Optional;

public interface EnrollmentPaymentStandingQuery {

    Optional<EnrollmentPaymentStandingView> findStandingOfProgramEnrollment(Long programEnrollmentId);

    Optional<EnrollmentPaymentStandingView> findStandingOfCourseEnrollment(Long courseEnrollmentId);

    List<EnrollmentPaymentStatusChangeView> listStatusChangesOfProgramEnrollment(Long programEnrollmentId);

    List<EnrollmentPaymentStatusChangeView> listStatusChangesOfCourseEnrollment(Long courseEnrollmentId);
}
