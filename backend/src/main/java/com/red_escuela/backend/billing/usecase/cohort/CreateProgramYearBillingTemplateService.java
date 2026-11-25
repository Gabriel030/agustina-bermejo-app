package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.configuration.service.CreateProgramYearBillingTemplateTransaction;
import com.red_escuela.backend.billing.dto.YearBillingConfigurationRequest;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CreateProgramYearBillingTemplateService implements CreateProgramYearBillingTemplateUseCase {
    private final CreateProgramYearBillingTemplateTransaction transaction;

    public CreateProgramYearBillingTemplateService(CreateProgramYearBillingTemplateTransaction transaction) {
        this.transaction = transaction;
    }

    @Override
    public ProgramYearBillingTemplate create(Long programId, Long termId, Integer recurringFeeInvoicesCount) {
        return create(programId, termId, recurringFeeInvoicesCount, null);
    }

    @Override
    public ProgramYearBillingTemplate create(Long programId, Long termId, Integer requestedInstallmentsCount, YearBillingConfigurationRequest configuration) {
        return transaction.create(programId, termId, requestedInstallmentsCount, configuration);
    }
}
