package com.red_escuela.backend.graduation.usecase.graduationrequest;

import java.time.Clock;
import com.red_escuela.backend.billing.api.EnrollmentWithdrawalBillingPort;
import com.red_escuela.backend.billing.api.WithdrawalCause;
import com.red_escuela.backend.billing.api.WithdrawnEnrollmentFacts;
import com.red_escuela.backend.billing.exception.PendingDebtException;
import com.red_escuela.backend.billing.service.InvoiceQueryService;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.exception.GraduationRequirementsNotMetException;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import com.red_escuela.backend.graduation.service.GraduationRequestFinder;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.notification.GraduationRequestApprovedEvent;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@UseCase
public class ApproveGraduationRequestService implements ApproveGraduationRequestUseCase {

    private final GraduationRequestRepository graduationRequestRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final GraduationRequestFinder graduationRequestFinder;
    private final ProgramEnrollmentService programEnrollmentService;
    private final InvoiceQueryService invoiceQueryService;
    private final CurrentPersonService currentPersonService;
    private final ApplicationEventPublisher eventPublisher;
    private final EnrollmentWithdrawalBillingPort withdrawalBillingPort;
    private final Clock clock;

    public ApproveGraduationRequestService(GraduationRequestRepository graduationRequestRepository,
                                            ProgramEnrollmentRepository programEnrollmentRepository,
                                            GraduationRequestFinder graduationRequestFinder,
                                            ProgramEnrollmentService programEnrollmentService,
                                            InvoiceQueryService invoiceQueryService,
                                            CurrentPersonService currentPersonService,
                                            ApplicationEventPublisher eventPublisher,
                                            EnrollmentWithdrawalBillingPort withdrawalBillingPort,
                                            Clock clock) {
        this.graduationRequestRepository = graduationRequestRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.graduationRequestFinder = graduationRequestFinder;
        this.programEnrollmentService = programEnrollmentService;
        this.invoiceQueryService = invoiceQueryService;
        this.currentPersonService = currentPersonService;
        this.eventPublisher = eventPublisher;
        this.withdrawalBillingPort = withdrawalBillingPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public GraduationRequest approve(Long graduationRequestId) {
        GraduationRequest request = graduationRequestFinder.findOrThrow(graduationRequestId);
        request.requireAwaitingDirectorDecision();
        ProgramEnrollment programEnrollment = request.getProgramEnrollment();
        programEnrollment.requireActive();
        if (!programEnrollmentService.calculateProgress(programEnrollment.getId()).elegibleParaCompletar()) {
            throw new GraduationRequirementsNotMetException(programEnrollment.getId());
        }
        Long studentId = programEnrollment.getStudent().getId();
        if (invoiceQueryService.hasDebt(studentId)) {
            throw new PendingDebtException(studentId);
        }

        request.approve(currentPersonService.resolveCurrentPerson(), LocalDateTime.now(clock));
        graduationRequestRepository.save(request);
        programEnrollmentRepository.save(programEnrollment);
        withdrawalBillingPort.registerWithdrawalForBilling(WithdrawnEnrollmentFacts.ofProgramEnrollment(
                studentId, programEnrollment.getId(), LocalDate.now(clock), WithdrawalCause.GRADUATION));

        eventPublisher.publishEvent(new GraduationRequestApprovedEvent(request, programEnrollment.getStudent().getPerson()));
        return request;
    }
}
