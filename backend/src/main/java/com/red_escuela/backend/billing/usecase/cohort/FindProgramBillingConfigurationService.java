package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.Percentage;

import java.time.Clock;
import java.time.LocalDate;

@UseCase
public class FindProgramBillingConfigurationService implements FindProgramBillingConfigurationUseCase {
    private final BillingConfigurationReader configurationReader;
    private final Clock clock;

    public FindProgramBillingConfigurationService(BillingConfigurationReader configurationReader, Clock clock) {
        this.configurationReader = configurationReader;
        this.clock = clock;
    }

    @Override
    public YearBillingTerms findYearTermsInEffect(ProgramYearBillingTemplate template) {
        return configurationReader.requireYearTermsEffectiveOn(template.programId(), template.getTerm().getId(), LocalDate.now(clock));
    }

    @Override
    public Percentage findEntryDiscountInEffect(ProgramCohort cohort) {
        return configurationReader.cohortEntryDiscountEffectiveOn(cohort.getId(), LocalDate.now(clock));
    }
}
