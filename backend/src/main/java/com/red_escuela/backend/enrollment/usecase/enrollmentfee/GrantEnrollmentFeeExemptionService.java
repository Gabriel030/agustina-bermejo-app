package com.red_escuela.backend.enrollment.usecase.enrollmentfee;

import com.red_escuela.backend.enrollment.dto.GrantEnrollmentFeeExemptionRequest;
import com.red_escuela.backend.enrollment.entity.EnrollmentFeeExemption;
import com.red_escuela.backend.enrollment.service.GrantEnrollmentFeeExemptionTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GrantEnrollmentFeeExemptionService implements GrantEnrollmentFeeExemptionUseCase {

    private final GrantEnrollmentFeeExemptionTransaction grantEnrollmentFeeExemptionTransaction;

    public GrantEnrollmentFeeExemptionService(GrantEnrollmentFeeExemptionTransaction grantEnrollmentFeeExemptionTransaction) {
        this.grantEnrollmentFeeExemptionTransaction = grantEnrollmentFeeExemptionTransaction;
    }

    @Override
    public EnrollmentFeeExemption grant(Long programEnrollmentId, GrantEnrollmentFeeExemptionRequest request) {
        return grantEnrollmentFeeExemptionTransaction.grant(programEnrollmentId, request);
    }
}
