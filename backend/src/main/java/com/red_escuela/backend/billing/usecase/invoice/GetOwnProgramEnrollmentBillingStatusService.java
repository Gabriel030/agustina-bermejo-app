package com.red_escuela.backend.billing.usecase.invoice;

import com.red_escuela.backend.billing.dto.MeEnrollmentBillingStatusResponse;
import com.red_escuela.backend.billing.reporting.service.OwnProgramEnrollmentBillingStatusReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetOwnProgramEnrollmentBillingStatusService implements GetOwnProgramEnrollmentBillingStatusUseCase {
    private final OwnProgramEnrollmentBillingStatusReader ownProgramEnrollmentBillingStatusReader;

    public GetOwnProgramEnrollmentBillingStatusService(OwnProgramEnrollmentBillingStatusReader ownProgramEnrollmentBillingStatusReader) {
        this.ownProgramEnrollmentBillingStatusReader = ownProgramEnrollmentBillingStatusReader;
    }

    @Override
    public MeEnrollmentBillingStatusResponse get(Long studentId, Long enrollmentId) {
        return ownProgramEnrollmentBillingStatusReader.readOwnProgramEnrollmentBillingStatus(studentId, enrollmentId);
    }
}
