package com.red_escuela.backend.enrollment.usecase.directorapproval;

import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequestStatus;
import com.red_escuela.backend.enrollment.service.DirectorApprovalRequestDecisionRecorder;
import com.red_escuela.backend.enrollment.service.DirectorApprovalRequestFinder;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class ApproveDirectorApprovalRequestService implements ApproveDirectorApprovalRequestUseCase {

    private final DirectorApprovalRequestFinder directorApprovalRequestFinder;
    private final DirectorApprovalRequestDecisionRecorder decisionRecorder;

    public ApproveDirectorApprovalRequestService(DirectorApprovalRequestFinder directorApprovalRequestFinder,
                                                  DirectorApprovalRequestDecisionRecorder decisionRecorder) {
        this.directorApprovalRequestFinder = directorApprovalRequestFinder;
        this.decisionRecorder = decisionRecorder;
    }

    @Override
    @Transactional
    public DirectorApprovalRequest approve(DecideDirectorApprovalRequestCommand command) {
        DirectorApprovalRequest entity = directorApprovalRequestFinder.findOrThrow(command.id());
        return decisionRecorder.record(entity, DirectorApprovalRequestStatus.APPROVED, command.notas());
    }
}
