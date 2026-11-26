package com.red_escuela.backend.enrollment.api;

import java.util.List;

public interface EnrollmentFeeRequirementPort {

    void requireForTerm(Long programEnrollmentId, Long termId, EnrollmentFeeRequirementPurpose purpose);

    void requireForCurrentPeriod(Long programEnrollmentId, EnrollmentFeeRequirementPurpose purpose);

    List<EnrollmentFeeObligationView> statusOf(Long programEnrollmentId);
}
