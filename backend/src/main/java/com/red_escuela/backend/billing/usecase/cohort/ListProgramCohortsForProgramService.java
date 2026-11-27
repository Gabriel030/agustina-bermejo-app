package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.billing.cohort.service.ProgramCohortLister;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListProgramCohortsForProgramService implements ListProgramCohortsForProgramUseCase {

    private final ProgramCohortLister programCohortLister;

    public ListProgramCohortsForProgramService(ProgramCohortLister programCohortLister) {
        this.programCohortLister = programCohortLister;
    }

    @Override
    public List<ProgramCohort> list(Long programId) {
        return programCohortLister.listForProgram(programId);
    }
}
