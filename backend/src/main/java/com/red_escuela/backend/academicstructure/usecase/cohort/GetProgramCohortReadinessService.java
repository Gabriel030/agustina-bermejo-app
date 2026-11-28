package com.red_escuela.backend.academicstructure.usecase.cohort;

import com.red_escuela.backend.academicstructure.dto.CohortReadinessResponse;
import com.red_escuela.backend.academicstructure.service.CohortReadinessChecker;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetProgramCohortReadinessService implements GetProgramCohortReadinessUseCase {

    private final CohortReadinessChecker readinessChecker;

    public GetProgramCohortReadinessService(CohortReadinessChecker readinessChecker) {
        this.readinessChecker = readinessChecker;
    }

    @Override
    public CohortReadinessResponse getProgramCohortReadiness(Long cohortId) {
        return readinessChecker.check(cohortId);
    }
}
