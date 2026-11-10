package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.service.CycleApproval;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ApproveCycleService implements ApproveCycleUseCase {

    private final CycleApproval cycleApproval;

    public ApproveCycleService(CycleApproval cycleApproval) {
        this.cycleApproval = cycleApproval;
    }

    @Override
    public void approve(Long cycleId, String idempotencyKey) {
        cycleApproval.approve(cycleId, idempotencyKey);
    }
}
