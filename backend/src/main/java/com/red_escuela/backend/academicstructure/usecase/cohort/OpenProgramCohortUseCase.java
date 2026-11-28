package com.red_escuela.backend.academicstructure.usecase.cohort;

import com.red_escuela.backend.academicstructure.dto.ProgramTermCohortResponse;

public interface OpenProgramCohortUseCase {
    ProgramTermCohortResponse openProgramCohort(Long cohortId);
}
