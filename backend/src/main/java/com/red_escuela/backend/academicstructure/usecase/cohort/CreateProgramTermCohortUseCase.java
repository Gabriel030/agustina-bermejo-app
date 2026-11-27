package com.red_escuela.backend.academicstructure.usecase.cohort;

import com.red_escuela.backend.academicstructure.dto.CreateProgramTermCohortRequest;
import com.red_escuela.backend.academicstructure.dto.ProgramTermCohortResponse;

public interface CreateProgramTermCohortUseCase {
    ProgramTermCohortResponse createProgramTermCohort(Long programId, Long termId, CreateProgramTermCohortRequest request);
}
