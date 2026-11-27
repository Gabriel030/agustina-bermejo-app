package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.dto.CreateProgramCohortRequest;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;

public interface CreateProgramCohortUseCase {
    ProgramCohort create(Long templateId, CreateProgramCohortRequest request);
}
