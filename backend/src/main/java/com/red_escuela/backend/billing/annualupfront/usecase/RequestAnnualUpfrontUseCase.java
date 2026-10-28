package com.red_escuela.backend.billing.annualupfront.usecase;

import com.red_escuela.backend.billing.annualupfront.service.RegisteredAnnualUpfrontRequest;

public interface RequestAnnualUpfrontUseCase {

    RegisteredAnnualUpfrontRequest requestAnnualUpfrontForCurrentStudent(Long programEnrollmentId, String acceptedTermsVersion);
}
