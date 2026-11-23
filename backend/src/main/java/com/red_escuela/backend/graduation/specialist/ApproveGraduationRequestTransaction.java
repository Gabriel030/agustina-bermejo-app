package com.red_escuela.backend.graduation.specialist;

import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.events.GraduationRequestApprovedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApproveGraduationRequestTransaction {

    private final GraduationRequestFinder graduationRequestFinder;
    private final GraduationRequestApprover graduationRequestApprover;
    private final GraduationDebtChecker graduationDebtChecker;
    private final GraduationDecisionRecorder graduationDecisionRecorder;
    private final ApplicationEventPublisher eventPublisher;

    public ApproveGraduationRequestTransaction(GraduationRequestFinder graduationRequestFinder,
                                               GraduationRequestApprover graduationRequestApprover,
                                               GraduationDebtChecker graduationDebtChecker,
                                               GraduationDecisionRecorder graduationDecisionRecorder,
                                               ApplicationEventPublisher eventPublisher) {
        this.graduationRequestFinder = graduationRequestFinder;
        this.graduationRequestApprover = graduationRequestApprover;
        this.graduationDebtChecker = graduationDebtChecker;
        this.graduationDecisionRecorder = graduationDecisionRecorder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public GraduationRequest approve(Long graduationRequestId) {
        GraduationRequest request = graduationRequestFinder.findOrThrow(graduationRequestId);
        Long studentId = graduationRequestApprover.requireApprovableAndFindStudentId(request);
        graduationDebtChecker.requireNoDebt(studentId);

        graduationDecisionRecorder.recordApproval(request);

        eventPublisher.publishEvent(GraduationRequestApprovedEvent.approvedFor(request));
        return request;
    }
}
