package com.red_escuela.backend.academicstructure.usecase.cohort;

import com.red_escuela.backend.academicstructure.dto.CreateProgramTermCohortRequest;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;

public interface CreateProgramTermCohortUseCase {
    ProgramCohort createProgramTermCohort(Long programId, Long termId, CreateProgramTermCohortRequest request);
}
