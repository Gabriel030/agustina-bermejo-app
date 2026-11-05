package com.red_escuela.backend.graduation.usecase.graduationrequest;

import com.red_escuela.backend.billing.exception.PendingDebtException;
import com.red_escuela.backend.billing.service.InvoiceQueryService;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.exception.InvalidGraduationRequestStateException;
import com.red_escuela.backend.graduation.events.GraduationRequestVerifiedEvent;
import com.red_escuela.backend.graduation.service.GraduationDebtGate;
import com.red_escuela.backend.graduation.service.GraduationDecisionRecorder;
import com.red_escuela.backend.graduation.service.GraduationRequestFinder;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class VerifyGraduationRequestDebtService implements VerifyGraduationRequestDebtUseCase {

    private final GraduationRequestFinder graduationRequestFinder;
    private final GraduationDebtGate graduationDebtGate;
    private final InvoiceQueryService invoiceQueryService;
    private final GraduationDecisionRecorder graduationDecisionRecorder;
    private final ApplicationEventPublisher eventPublisher;

    public VerifyGraduationRequestDebtService(GraduationRequestFinder graduationRequestFinder,
                                               GraduationDebtGate graduationDebtGate,
                                               InvoiceQueryService invoiceQueryService,
                                               GraduationDecisionRecorder graduationDecisionRecorder,
                                               ApplicationEventPublisher eventPublisher) {
        this.graduationRequestFinder = graduationRequestFinder;
        this.graduationDebtGate = graduationDebtGate;
        this.invoiceQueryService = invoiceQueryService;
        this.graduationDecisionRecorder = graduationDecisionRecorder;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public GraduationRequest verify(Long graduationRequestId) {
        GraduationRequest request = graduationRequestFinder.findOrThrow(graduationRequestId);
        if (request.getStatus() != GraduationRequestStatus.REQUESTED) {
            throw new InvalidGraduationRequestStateException(graduationRequestId);
        }

        Long studentId = request.getProgramEnrollment().getStudent().getId();
        if (graduationDebtGate.mustCheckDebt() && invoiceQueryService.hasDebt(studentId)) {
            throw new PendingDebtException(studentId);
        }

        GraduationRequest saved = graduationDecisionRecorder.recordVerification(request);
        eventPublisher.publishEvent(GraduationRequestVerifiedEvent.verifiedFor(saved));
        return saved;
    }
}
