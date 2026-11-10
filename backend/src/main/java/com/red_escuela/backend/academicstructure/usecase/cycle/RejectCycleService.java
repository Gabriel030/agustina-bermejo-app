package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.service.CycleRejection;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RejectCycleService implements RejectCycleUseCase {

    private final CycleRejection cycleRejection;

    public RejectCycleService(CycleRejection cycleRejection) {
        this.cycleRejection = cycleRejection;
    }

    @Override
    public void reject(Long cycleId, String reason, String idempotencyKey) {
        cycleRejection.reject(cycleId, reason, idempotencyKey);
    }
}
