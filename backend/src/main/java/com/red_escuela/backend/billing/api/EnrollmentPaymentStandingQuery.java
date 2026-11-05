package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface EnrollmentPaymentStandingQuery {

    Optional<EnrollmentPaymentStandingView> findStandingOfProgramEnrollment(Long programEnrollmentId);

    Optional<EnrollmentPaymentStandingView> findStandingOfCourseEnrollment(Long courseEnrollmentId);

    Map<Long, EnrollmentPaymentStatus> findStatusesOfProgramEnrollments(Collection<Long> programEnrollmentIds);

    Map<Long, EnrollmentPaymentStatus> findStatusesOfCourseEnrollments(Collection<Long> courseEnrollmentIds);

    List<EnrollmentPaymentStatusChangeView> listStatusChangesOfProgramEnrollment(Long programEnrollmentId);

    List<EnrollmentPaymentStatusChangeView> listStatusChangesOfCourseEnrollment(Long courseEnrollmentId);
}
