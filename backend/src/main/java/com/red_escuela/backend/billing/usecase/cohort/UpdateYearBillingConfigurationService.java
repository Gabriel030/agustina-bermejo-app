package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.configuration.service.UpdateYearBillingConfigurationTransaction;
import com.red_escuela.backend.billing.dto.YearBillingConfigurationRequest;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdateYearBillingConfigurationService implements UpdateYearBillingConfigurationUseCase {
    private final UpdateYearBillingConfigurationTransaction transaction;

    public UpdateYearBillingConfigurationService(UpdateYearBillingConfigurationTransaction transaction) {
        this.transaction = transaction;
    }

    @Override
    public ProgramYearBillingTemplate update(Long templateId, YearBillingConfigurationRequest request) {
        return transaction.update(templateId, request);
    }
}
