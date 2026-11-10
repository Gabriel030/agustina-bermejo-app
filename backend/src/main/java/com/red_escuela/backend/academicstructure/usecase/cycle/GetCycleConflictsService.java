package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.CycleConflictsResponse;
import com.red_escuela.backend.academicstructure.service.CycleConflictFinder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetCycleConflictsService implements GetCycleConflictsUseCase {

    private final CycleConflictFinder conflictFinder;

    public GetCycleConflictsService(CycleConflictFinder conflictFinder) {
        this.conflictFinder = conflictFinder;
    }

    @Override
    public CycleConflictsResponse getConflicts(Long cycleId) {
        return conflictFinder.find(cycleId);
    }
}
