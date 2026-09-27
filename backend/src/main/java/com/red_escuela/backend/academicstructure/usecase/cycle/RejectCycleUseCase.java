package com.red_escuela.backend.academicstructure.usecase.cycle;

public interface RejectCycleUseCase {
    void reject(Long cycleId, String reason);
}
