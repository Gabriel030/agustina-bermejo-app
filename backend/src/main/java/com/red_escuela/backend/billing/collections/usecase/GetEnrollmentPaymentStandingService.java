package com.red_escuela.backend.billing.collections.usecase;

import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import com.red_escuela.backend.billing.collections.dto.EnrollmentPaymentStandingResponse;
import com.red_escuela.backend.billing.collections.dto.EnrollmentPaymentStatusChangeResponse;
import com.red_escuela.backend.billing.collections.service.EnrollmentPaymentStandingReader;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class GetEnrollmentPaymentStandingService implements GetEnrollmentPaymentStandingUseCase {

    private final EnrollmentPaymentStandingReader reader;

    public GetEnrollmentPaymentStandingService(EnrollmentPaymentStandingReader reader) {
        this.reader = reader;
    }

    @Override
    public EnrollmentPaymentStandingResponse getStandingOfEnrollment(EnrollmentKind kind, Long enrollmentId) {
        return reader.readStanding(kind, enrollmentId);
    }

    @Override
    public List<EnrollmentPaymentStatusChangeResponse> listStatusChangesOfEnrollment(EnrollmentKind kind, Long enrollmentId) {
        return reader.readHistory(kind, enrollmentId);
    }
}
