package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.entity.ProgramCohort;

import java.util.List;

public interface ListProgramCohortsForProgramUseCase {
    List<ProgramCohort> list(Long programId);
}
