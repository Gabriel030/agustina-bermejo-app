package com.red_escuela.backend.enrollment.usecase.enrollmentfee;

public interface RevokeEnrollmentFeeExemptionUseCase {
    void revoke(Long exemptionId, String notes);
}
