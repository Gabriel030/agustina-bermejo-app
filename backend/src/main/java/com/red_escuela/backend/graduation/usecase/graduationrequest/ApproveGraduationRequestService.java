package com.red_escuela.backend.graduation.usecase.graduationrequest;

import com.red_escuela.backend.billing.api.StudentDebtView;
import com.red_escuela.backend.billing.exception.PendingDebtException;
import com.red_escuela.backend.billing.service.InvoiceQueryService;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.service.GraduationDebtGate;
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
    private final GraduationDebtGate graduationDebtGate;
    private final InvoiceQueryService invoiceQueryService;
    private final GraduationDecisionRecorder graduationDecisionRecorder;
    private final ApplicationEventPublisher eventPublisher;

    public ApproveGraduationRequestService(GraduationRequestFinder graduationRequestFinder,
                                            GraduationRequestApprover graduationRequestApprover,
                                            GraduationDebtGate graduationDebtGate,
                                            InvoiceQueryService invoiceQueryService,
                                            GraduationDecisionRecorder graduationDecisionRecorder,
                                            ApplicationEventPublisher eventPublisher) {
        this.graduationRequestFinder = graduationRequestFinder;
        this.graduationRequestApprover = graduationRequestApprover;
        this.graduationDebtGate = graduationDebtGate;
        this.invoiceQueryService = invoiceQueryService;
        this.graduationDecisionRecorder = graduationDecisionRecorder;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public GraduationRequest approve(Long graduationRequestId) {
        GraduationRequest request = graduationRequestFinder.findOrThrow(graduationRequestId);
        Long studentId = graduationRequestApprover.requireApprovableAndFindStudentId(request);
        if (graduationDebtGate.mustCheckDebt() && invoiceQueryService.hasDebt(studentId)) {
            StudentDebtView debt = invoiceQueryService.debtOf(studentId);
            throw new PendingDebtException(studentId, debt.totalDue(), debt.installmentCount());
        }

        graduationDecisionRecorder.recordApproval(request);

        eventPublisher.publishEvent(GraduationRequestApprovedEvent.approvedFor(request));
        return request;
    }
}
