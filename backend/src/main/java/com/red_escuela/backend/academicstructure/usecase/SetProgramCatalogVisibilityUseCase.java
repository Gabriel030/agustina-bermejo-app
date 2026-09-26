package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.dto.ProgramResponse;

public interface SetProgramCatalogVisibilityUseCase {
    ProgramResponse execute(Long programId, boolean visible);
}
