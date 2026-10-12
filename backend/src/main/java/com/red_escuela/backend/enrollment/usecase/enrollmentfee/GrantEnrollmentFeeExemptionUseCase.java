package com.red_escuela.backend.enrollment.usecase.enrollmentfee;

import com.red_escuela.backend.enrollment.dto.GrantEnrollmentFeeExemptionRequest;
import com.red_escuela.backend.enrollment.entity.EnrollmentFeeExemption;

public interface GrantEnrollmentFeeExemptionUseCase {
    EnrollmentFeeExemption grant(Long programEnrollmentId, GrantEnrollmentFeeExemptionRequest request);
}
