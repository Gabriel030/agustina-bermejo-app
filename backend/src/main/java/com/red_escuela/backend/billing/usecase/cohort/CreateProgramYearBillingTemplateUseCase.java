package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;

public interface CreateProgramYearBillingTemplateUseCase {
    ProgramYearBillingTemplate create(Long programId, Long termId, Integer recurringFeeInvoicesCount);
}
