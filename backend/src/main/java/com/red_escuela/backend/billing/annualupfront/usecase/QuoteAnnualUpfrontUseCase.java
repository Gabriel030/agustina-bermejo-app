package com.red_escuela.backend.billing.annualupfront.usecase;

import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontEligibility;

public interface QuoteAnnualUpfrontUseCase {

    AnnualUpfrontEligibility quoteAnnualUpfrontForCurrentStudent(Long programEnrollmentId);

    AnnualUpfrontEligibility quoteAnnualUpfrontForStaff(Long programEnrollmentId);
}
