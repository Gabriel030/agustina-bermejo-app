package com.red_escuela.backend.enrollment.usecase.enrollmentfee;

import com.red_escuela.backend.enrollment.dto.EnrollmentFeeExemptionResponse;

import java.util.List;

public interface ListEnrollmentFeeExemptionsUseCase {
    List<EnrollmentFeeExemptionResponse> list(Long programEnrollmentId);
}
