package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.repository.ProgramCohortRepository;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListProgramCohortsForProgramService implements ListProgramCohortsForProgramUseCase {

    private final ProgramCohortRepository programCohortRepository;

    public ListProgramCohortsForProgramService(ProgramCohortRepository programCohortRepository) {
        this.programCohortRepository = programCohortRepository;
    }

    @Override
    public List<ProgramCohort> list(Long programId) {
        return programCohortRepository.findByTemplate_ProgramId(programId);
    }
}
