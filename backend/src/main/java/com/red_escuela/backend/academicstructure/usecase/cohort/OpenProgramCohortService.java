package com.red_escuela.backend.academicstructure.usecase.cohort;

import com.red_escuela.backend.academicstructure.dto.ProgramTermCohortResponse;
import com.red_escuela.backend.academicstructure.service.ProgramCohortOpening;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class OpenProgramCohortService implements OpenProgramCohortUseCase {

    private final ProgramCohortOpening cohortOpening;

    public OpenProgramCohortService(ProgramCohortOpening cohortOpening) {
        this.cohortOpening = cohortOpening;
    }

    @Override
    public ProgramTermCohortResponse openProgramCohort(Long cohortId) {
        return ProgramTermCohortResponse.from(cohortOpening.open(cohortId));
    }
}
