package com.red_escuela.backend.graduation.usecase.graduationrequest;

import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.service.GraduationDebtChecker;
import com.red_escuela.backend.graduation.service.GraduationDecisionRecorder;
import com.red_escuela.backend.graduation.service.GraduationRequestApprover;
import com.red_escuela.backend.graduation.service.GraduationRequestFinder;
import com.red_escuela.backend.graduation.events.GraduationRequestApprovedEvent;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class ApproveGraduationRequestService implements ApproveGraduationRequestUseCase {

    private final GraduationRequestFinder graduationRequestFinder;
    private final GraduationRequestApprover graduationRequestApprover;
    private final GraduationDebtChecker graduationDebtChecker;
    private final GraduationDecisionRecorder graduationDecisionRecorder;
    private final ApplicationEventPublisher eventPublisher;

    public ApproveGraduationRequestService(GraduationRequestFinder graduationRequestFinder,
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

    @Override
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
