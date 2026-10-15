package com.red_escuela.backend.billing.usecase.invoice;

import com.red_escuela.backend.billing.dto.MeEnrollmentBillingStatusResponse;

public interface GetOwnProgramEnrollmentBillingStatusUseCase {
    MeEnrollmentBillingStatusResponse get(Long studentId, Long enrollmentId);
}
