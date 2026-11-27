package com.red_escuela.backend.academicstructure.usecase.cohort;

import com.red_escuela.backend.academicstructure.dto.CreateProgramTermCohortRequest;
import com.red_escuela.backend.academicstructure.dto.ProgramTermCohortResponse;
import com.red_escuela.backend.academicstructure.service.ProgramTermCohortCreation;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CreateProgramTermCohortService implements CreateProgramTermCohortUseCase {

    private final ProgramTermCohortCreation cohortCreation;

    public CreateProgramTermCohortService(ProgramTermCohortCreation cohortCreation) {
        this.cohortCreation = cohortCreation;
    }

    @Override
    public ProgramTermCohortResponse createProgramTermCohort(Long programId, Long termId, CreateProgramTermCohortRequest request) {
        return ProgramTermCohortResponse.from(cohortCreation.create(programId, termId, request));
    }
}
