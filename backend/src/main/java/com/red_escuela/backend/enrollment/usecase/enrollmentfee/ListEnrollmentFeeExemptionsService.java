package com.red_escuela.backend.enrollment.usecase.enrollmentfee;

import com.red_escuela.backend.enrollment.dto.EnrollmentFeeExemptionResponse;
import com.red_escuela.backend.enrollment.service.EnrollmentFeeReader;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListEnrollmentFeeExemptionsService implements ListEnrollmentFeeExemptionsUseCase {

    private final EnrollmentFeeReader feeReader;

    public ListEnrollmentFeeExemptionsService(EnrollmentFeeReader feeReader) {
        this.feeReader = feeReader;
    }

    @Override
    public List<EnrollmentFeeExemptionResponse> list(Long programEnrollmentId) {
        return feeReader.listExemptions(programEnrollmentId);
    }
}
