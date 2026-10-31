package com.red_escuela.backend.graduation.usecase.graduationrequest;

import java.time.Clock;
import com.red_escuela.backend.billing.exception.PendingDebtException;
import com.red_escuela.backend.billing.service.InvoiceQueryService;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.exception.InvalidGraduationRequestStateException;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import com.red_escuela.backend.graduation.events.GraduationRequestVerifiedEvent;
import com.red_escuela.backend.graduation.service.GraduationDebtGate;
import com.red_escuela.backend.graduation.service.GraduationRequestFinder;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@UseCase
public class VerifyGraduationRequestDebtService implements VerifyGraduationRequestDebtUseCase {

    private final GraduationRequestRepository graduationRequestRepository;
    private final GraduationRequestFinder graduationRequestFinder;
    private final GraduationDebtGate graduationDebtGate;
    private final InvoiceQueryService invoiceQueryService;
    private final CurrentPersonService currentPersonService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public VerifyGraduationRequestDebtService(GraduationRequestRepository graduationRequestRepository,
                                               GraduationRequestFinder graduationRequestFinder,
                                               GraduationDebtGate graduationDebtGate,
                                               InvoiceQueryService invoiceQueryService,
                                               CurrentPersonService currentPersonService,
                                               ApplicationEventPublisher eventPublisher,
                                               Clock clock) {
        this.graduationRequestRepository = graduationRequestRepository;
        this.graduationRequestFinder = graduationRequestFinder;
        this.graduationDebtGate = graduationDebtGate;
        this.invoiceQueryService = invoiceQueryService;
        this.currentPersonService = currentPersonService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
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

        request.setStatus(GraduationRequestStatus.ADMIN_VERIFIED);
        request.setVerifiedBy(currentPersonService.resolveCurrentPerson());
        request.setVerifiedAt(LocalDateTime.now(clock));
        GraduationRequest saved = graduationRequestRepository.save(request);
        eventPublisher.publishEvent(GraduationRequestVerifiedEvent.verifiedFor(saved));
        return saved;
    }
}
