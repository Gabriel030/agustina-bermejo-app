package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.dto.YearBillingConfigurationRequest;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;

public interface UpdateYearBillingConfigurationUseCase {
    ProgramYearBillingTemplate update(Long templateId, YearBillingConfigurationRequest request);
}
