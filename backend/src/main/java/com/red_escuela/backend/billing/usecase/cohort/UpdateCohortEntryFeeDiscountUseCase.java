package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.entity.ProgramCohort;

import java.math.BigDecimal;

public interface UpdateCohortEntryFeeDiscountUseCase {
    ProgramCohort update(Long cohortId, BigDecimal discountPoints);
}
