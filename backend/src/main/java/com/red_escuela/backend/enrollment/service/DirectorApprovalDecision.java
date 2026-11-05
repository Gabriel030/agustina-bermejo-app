package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequestStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DirectorApprovalDecision {

    private final DirectorApprovalRequestFinder directorApprovalRequestFinder;
    private final DirectorApprovalRequestDecisionRecorder decisionRecorder;

    public DirectorApprovalDecision(DirectorApprovalRequestFinder directorApprovalRequestFinder,
                                    DirectorApprovalRequestDecisionRecorder decisionRecorder) {
        this.directorApprovalRequestFinder = directorApprovalRequestFinder;
        this.decisionRecorder = decisionRecorder;
    }

    @Transactional
    public DirectorApprovalRequest approve(Long requestId, String notes) {
        return decide(requestId, DirectorApprovalRequestStatus.APPROVED, notes);
    }

    @Transactional
    public DirectorApprovalRequest reject(Long requestId, String notes) {
        return decide(requestId, DirectorApprovalRequestStatus.REJECTED, notes);
    }

    private DirectorApprovalRequest decide(Long requestId, DirectorApprovalRequestStatus status, String notes) {
        DirectorApprovalRequest entity = directorApprovalRequestFinder.findOrThrow(requestId);
        return decisionRecorder.record(entity, status, notes);
    }
}
