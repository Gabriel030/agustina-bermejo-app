package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.cohort.service.ProgramCohortCatalog;
import com.red_escuela.backend.billing.dto.ProgramCohortCatalogResponse;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListProgramCohortsForProgramService implements ListProgramCohortsForProgramUseCase {

    private final ProgramCohortCatalog programCohortCatalog;

    public ListProgramCohortsForProgramService(ProgramCohortCatalog programCohortCatalog) {
        this.programCohortCatalog = programCohortCatalog;
    }

    @Override
    public List<ProgramCohortCatalogResponse> list(Long programId) {
        return programCohortCatalog.list(programId);
    }
}
