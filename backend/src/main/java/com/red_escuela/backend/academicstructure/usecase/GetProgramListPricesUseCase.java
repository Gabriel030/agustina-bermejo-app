package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.dto.ProgramListPrices;

public interface GetProgramListPricesUseCase {
    ProgramListPrices execute(Long programId);
}
