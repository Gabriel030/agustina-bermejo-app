package com.red_escuela.backend.billing.collections.usecase;

import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import com.red_escuela.backend.billing.collections.dto.EnrollmentPaymentStandingResponse;
import com.red_escuela.backend.billing.collections.dto.EnrollmentPaymentStatusChangeResponse;

import java.util.List;

public interface GetEnrollmentPaymentStandingUseCase {

    EnrollmentPaymentStandingResponse getStandingOfEnrollment(EnrollmentKind kind, Long enrollmentId);

    List<EnrollmentPaymentStatusChangeResponse> listStatusChangesOfEnrollment(EnrollmentKind kind, Long enrollmentId);
}
