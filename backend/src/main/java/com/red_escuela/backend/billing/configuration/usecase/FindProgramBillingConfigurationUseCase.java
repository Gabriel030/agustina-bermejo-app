package com.red_escuela.backend.billing.configuration.usecase;

import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.shared.domain.Percentage;

public interface FindProgramBillingConfigurationUseCase {
    YearBillingTerms findYearTermsInEffect(Long programId, Long termId);

    Percentage findEntryDiscountInEffect(Long cohortId);
}
