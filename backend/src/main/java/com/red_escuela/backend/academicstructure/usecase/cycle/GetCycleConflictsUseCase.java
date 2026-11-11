package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.CycleConflictsResponse;

public interface GetCycleConflictsUseCase {
    CycleConflictsResponse getConflicts(Long cycleId);
}
