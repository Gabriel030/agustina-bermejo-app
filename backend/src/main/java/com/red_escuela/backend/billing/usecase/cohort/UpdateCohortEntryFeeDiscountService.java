package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.cohort.service.UpdateCohortEntryFeeDiscountTransaction;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.Percentage;

@UseCase
public class UpdateCohortEntryFeeDiscountService implements UpdateCohortEntryFeeDiscountUseCase {

    private final UpdateCohortEntryFeeDiscountTransaction updateTransaction;

    public UpdateCohortEntryFeeDiscountService(UpdateCohortEntryFeeDiscountTransaction updateTransaction) {
        this.updateTransaction = updateTransaction;
    }

    @Override
    public ProgramCohort update(Long cohortId, Percentage discount) {
        return updateTransaction.update(cohortId, discount);
    }
}
