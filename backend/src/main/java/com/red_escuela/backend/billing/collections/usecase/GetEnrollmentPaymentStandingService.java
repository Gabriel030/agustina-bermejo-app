package com.red_escuela.backend.billing.collections.usecase;

import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import com.red_escuela.backend.billing.collections.dto.EnrollmentPaymentStandingResponse;
import com.red_escuela.backend.billing.collections.dto.EnrollmentPaymentStatusChangeResponse;
import com.red_escuela.backend.billing.collections.service.EnrollmentPaymentStandingReader;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class GetEnrollmentPaymentStandingService implements GetEnrollmentPaymentStandingUseCase {

    private final EnrollmentPaymentStandingReader reader;

    public GetEnrollmentPaymentStandingService(EnrollmentPaymentStandingReader reader) {
        this.reader = reader;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR', 'PRECEPTOR')")
    public EnrollmentPaymentStandingResponse getStandingOfEnrollment(EnrollmentKind kind, Long enrollmentId) {
        return reader.readStanding(kind, enrollmentId);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<EnrollmentPaymentStatusChangeResponse> listStatusChangesOfEnrollment(EnrollmentKind kind, Long enrollmentId) {
        return reader.readHistory(kind, enrollmentId);
    }
}
