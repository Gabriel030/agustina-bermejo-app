package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.entity.ProgramCohort;

public interface UpdateCohortEnrollmentFeeMonthUseCase {
    ProgramCohort update(Long cohortId, Integer enrollmentFeeMonth);
}
