package com.red_escuela.backend.enrollment.usecase.directorapproval;

import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.enrollment.service.DirectorApprovalDecision;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ApproveDirectorApprovalRequestService implements ApproveDirectorApprovalRequestUseCase {

    private final DirectorApprovalDecision directorApprovalDecision;

    public ApproveDirectorApprovalRequestService(DirectorApprovalDecision directorApprovalDecision) {
        this.directorApprovalDecision = directorApprovalDecision;
    }

    @Override
    public DirectorApprovalRequest approve(DecideDirectorApprovalRequestCommand command) {
        return directorApprovalDecision.approve(command.id(), command.notas());
    }
}
