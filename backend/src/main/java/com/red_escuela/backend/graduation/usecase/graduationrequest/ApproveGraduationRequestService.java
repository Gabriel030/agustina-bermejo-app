package com.red_escuela.backend.graduation.usecase.graduationrequest;

import java.time.Clock;
import com.red_escuela.backend.billing.exception.PendingDebtException;
import com.red_escuela.backend.billing.service.InvoiceQueryService;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.service.GraduationRequestApprover;
import com.red_escuela.backend.graduation.service.GraduationRequestFinder;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.graduation.events.GraduationRequestApprovedEvent;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@UseCase
public class ApproveGraduationRequestService implements ApproveGraduationRequestUseCase {

    private final GraduationRequestFinder graduationRequestFinder;
    private final GraduationRequestApprover graduationRequestApprover;
    private final InvoiceQueryService invoiceQueryService;
    private final CurrentPersonService currentPersonService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ApproveGraduationRequestService(GraduationRequestFinder graduationRequestFinder,
                                            GraduationRequestApprover graduationRequestApprover,
                                            InvoiceQueryService invoiceQueryService,
                                            CurrentPersonService currentPersonService,
                                            ApplicationEventPublisher eventPublisher,
                                            Clock clock) {
        this.graduationRequestFinder = graduationRequestFinder;
        this.graduationRequestApprover = graduationRequestApprover;
        this.invoiceQueryService = invoiceQueryService;
        this.currentPersonService = currentPersonService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public GraduationRequest approve(Long graduationRequestId) {
        GraduationRequest request = graduationRequestFinder.findOrThrow(graduationRequestId);
        Long studentId = graduationRequestApprover.requireApprovableAndFindStudentId(request);
        if (invoiceQueryService.hasDebt(studentId)) {
            throw new PendingDebtException(studentId);
        }

        graduationRequestApprover.recordApproval(request, currentPersonService.resolveCurrentPerson(), LocalDateTime.now(clock));

        eventPublisher.publishEvent(GraduationRequestApprovedEvent.approvedFor(request));
        return request;
    }
}
