package com.red_escuela.backend.graduation.usecase.graduationrequest;

import com.red_escuela.backend.billing.exception.PendingDebtException;
import com.red_escuela.backend.billing.service.InvoiceQueryService;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.exception.InvalidGraduationRequestStateException;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import com.red_escuela.backend.graduation.service.GraduationRequestFinder;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@UseCase
public class VerifyGraduationRequestDebtService implements VerifyGraduationRequestDebtUseCase {

    private final GraduationRequestRepository graduationRequestRepository;
    private final GraduationRequestFinder graduationRequestFinder;
    private final InvoiceQueryService invoiceQueryService;
    private final CurrentPersonService currentPersonService;

    public VerifyGraduationRequestDebtService(GraduationRequestRepository graduationRequestRepository,
                                               GraduationRequestFinder graduationRequestFinder,
                                               InvoiceQueryService invoiceQueryService,
                                               CurrentPersonService currentPersonService) {
        this.graduationRequestRepository = graduationRequestRepository;
        this.graduationRequestFinder = graduationRequestFinder;
        this.invoiceQueryService = invoiceQueryService;
        this.currentPersonService = currentPersonService;
    }

    @Override
    @Transactional
    public GraduationRequest verify(Long graduationRequestId) {
        GraduationRequest request = graduationRequestFinder.findOrThrow(graduationRequestId);
        if (request.getStatus() != GraduationRequestStatus.REQUESTED) {
            throw new InvalidGraduationRequestStateException(graduationRequestId);
        }

        Long studentId = request.getProgramEnrollment().getStudent().getId();
        if (invoiceQueryService.hasDebt(studentId)) {
            throw new PendingDebtException(studentId);
        }

        request.setStatus(GraduationRequestStatus.ADMIN_VERIFIED);
        request.setVerifiedBy(currentPersonService.resolveCurrentPerson());
        request.setVerifiedAt(LocalDateTime.now());
        return graduationRequestRepository.save(request);
    }
}
