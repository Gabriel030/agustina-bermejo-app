package com.red_escuela.backend.enrollment.usecase.enrollmentfee;

import com.red_escuela.backend.enrollment.dto.EnrollmentFeeObligationResponse;
import com.red_escuela.backend.enrollment.service.EnrollmentFeeReader;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class GetEnrollmentFeeStatusService implements GetEnrollmentFeeStatusUseCase {

    private final EnrollmentFeeReader feeReader;

    public GetEnrollmentFeeStatusService(EnrollmentFeeReader feeReader) {
        this.feeReader = feeReader;
    }

    @Override
    public List<EnrollmentFeeObligationResponse> get(Long programEnrollmentId) {
        return feeReader.readFeeStatus(programEnrollmentId);
    }
}
