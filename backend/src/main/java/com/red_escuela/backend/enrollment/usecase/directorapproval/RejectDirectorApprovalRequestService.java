package com.red_escuela.backend.enrollment.usecase.directorapproval;

import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.enrollment.service.DirectorApprovalDecision;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RejectDirectorApprovalRequestService implements RejectDirectorApprovalRequestUseCase {

    private final DirectorApprovalDecision directorApprovalDecision;

    public RejectDirectorApprovalRequestService(DirectorApprovalDecision directorApprovalDecision) {
        this.directorApprovalDecision = directorApprovalDecision;
    }

    @Override
    public DirectorApprovalRequest reject(DecideDirectorApprovalRequestCommand command) {
        return directorApprovalDecision.reject(command.id(), command.notas());
    }
}
