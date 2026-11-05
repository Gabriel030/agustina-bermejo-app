package com.red_escuela.backend.billing.usecase.invoice;

import com.red_escuela.backend.billing.dto.MeEnrollmentBillingStatusResponse;
import com.red_escuela.backend.billing.reporting.service.OwnCourseEnrollmentBillingStatusReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetOwnCourseEnrollmentBillingStatusService implements GetOwnCourseEnrollmentBillingStatusUseCase {
    private final OwnCourseEnrollmentBillingStatusReader ownCourseEnrollmentBillingStatusReader;

    public GetOwnCourseEnrollmentBillingStatusService(OwnCourseEnrollmentBillingStatusReader ownCourseEnrollmentBillingStatusReader) {
        this.ownCourseEnrollmentBillingStatusReader = ownCourseEnrollmentBillingStatusReader;
    }

    @Override
    public MeEnrollmentBillingStatusResponse get(Long studentId, Long enrollmentId) {
        return ownCourseEnrollmentBillingStatusReader.readOwnCourseEnrollmentBillingStatus(studentId, enrollmentId);
    }
}
