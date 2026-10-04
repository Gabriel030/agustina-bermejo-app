package com.red_escuela.backend.enrollment.usecase.enrollmentfee;

import com.red_escuela.backend.enrollment.dto.EnrollmentFeeObligationResponse;

import java.util.List;

public interface GetEnrollmentFeeStatusUseCase {
    List<EnrollmentFeeObligationResponse> get(Long programEnrollmentId);
}
