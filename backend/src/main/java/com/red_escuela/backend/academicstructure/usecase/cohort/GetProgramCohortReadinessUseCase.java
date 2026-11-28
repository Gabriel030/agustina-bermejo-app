package com.red_escuela.backend.academicstructure.usecase.cohort;

import com.red_escuela.backend.academicstructure.dto.CohortReadinessResponse;

public interface GetProgramCohortReadinessUseCase {
    CohortReadinessResponse getProgramCohortReadiness(Long cohortId);
}
