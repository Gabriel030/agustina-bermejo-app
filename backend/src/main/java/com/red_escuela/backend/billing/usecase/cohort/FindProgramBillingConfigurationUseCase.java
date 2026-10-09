package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.shared.domain.Percentage;

public interface FindProgramBillingConfigurationUseCase {
    YearBillingTerms findYearTermsInEffect(ProgramYearBillingTemplate template);

    Percentage findEntryDiscountInEffect(ProgramCohort cohort);
}
