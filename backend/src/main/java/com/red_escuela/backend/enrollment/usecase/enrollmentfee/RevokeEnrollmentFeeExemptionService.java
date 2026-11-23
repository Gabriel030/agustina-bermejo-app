package com.red_escuela.backend.enrollment.usecase.enrollmentfee;

import com.red_escuela.backend.enrollment.service.RevokeEnrollmentFeeExemptionTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RevokeEnrollmentFeeExemptionService implements RevokeEnrollmentFeeExemptionUseCase {

    private final RevokeEnrollmentFeeExemptionTransaction revokeEnrollmentFeeExemptionTransaction;

    public RevokeEnrollmentFeeExemptionService(RevokeEnrollmentFeeExemptionTransaction revokeEnrollmentFeeExemptionTransaction) {
        this.revokeEnrollmentFeeExemptionTransaction = revokeEnrollmentFeeExemptionTransaction;
    }

    @Override
    public void revoke(Long exemptionId, String notes) {
        revokeEnrollmentFeeExemptionTransaction.revoke(exemptionId, notes);
    }
}
