package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.billing.entity.ProgramCohort;


public interface UpdateCohortEntryFeeDiscountUseCase {
    ProgramCohort update(Long cohortId, Percentage discount);
}
