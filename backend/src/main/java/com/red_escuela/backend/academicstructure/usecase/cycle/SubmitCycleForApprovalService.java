package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.service.CycleSubmission;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class SubmitCycleForApprovalService implements SubmitCycleForApprovalUseCase {

    private final CycleSubmission cycleSubmission;

    public SubmitCycleForApprovalService(CycleSubmission cycleSubmission) {
        this.cycleSubmission = cycleSubmission;
    }

    @Override
    public void submit(Long cycleId) {
        cycleSubmission.submit(cycleId);
    }
}
