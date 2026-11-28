package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.dto.ProgramCohortCatalogResponse;

import java.util.List;

public interface ListProgramCohortsForProgramUseCase {
    List<ProgramCohortCatalogResponse> list(Long programId);
}
