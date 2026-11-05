package com.red_escuela.backend.graduation.service;

import com.red_escuela.backend.billing.api.StudentDebtView;
import com.red_escuela.backend.billing.exception.PendingDebtException;
import com.red_escuela.backend.billing.service.InvoiceQueryService;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.events.GraduationRequestDebtBlockedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GraduationDebtChecker {

    private final GraduationDebtGate graduationDebtGate;
    private final InvoiceQueryService invoiceQueryService;
    private final GraduationRequestFinder graduationRequestFinder;
    private final GraduationDebtBlockPublisher graduationDebtBlockPublisher;

    public GraduationDebtChecker(GraduationDebtGate graduationDebtGate, InvoiceQueryService invoiceQueryService,
                                 GraduationRequestFinder graduationRequestFinder, GraduationDebtBlockPublisher graduationDebtBlockPublisher) {
        this.graduationDebtGate = graduationDebtGate;
        this.invoiceQueryService = invoiceQueryService;
        this.graduationRequestFinder = graduationRequestFinder;
        this.graduationDebtBlockPublisher = graduationDebtBlockPublisher;
    }

    public void requireNoDebt(Long studentId) {
        StudentDebtView debt = currentDebtOf(studentId);
        if (debt.installmentCount() > 0) {
            throw pendingDebtOf(studentId, debt);
        }
    }

    public void requireNoDebtNotifying(GraduationRequest request, Long studentId) {
        StudentDebtView debt = currentDebtOf(studentId);
        if (debt.installmentCount() > 0) {
            graduationDebtBlockPublisher.publishDebtBlocked(GraduationRequestDebtBlockedEvent.blockedFor(request, debt.totalDue(), debt.installmentCount()));
            throw pendingDebtOf(studentId, debt);
        }
    }

    @Transactional(readOnly = true)
    public GraduationDebtSummary summaryOf(Long graduationRequestId) {
        GraduationRequest request = graduationRequestFinder.findOrThrow(graduationRequestId);
        if (!graduationDebtGate.mustCheckDebt()) {
            return new GraduationDebtSummary(false, StudentDebtView.none());
        }
        return new GraduationDebtSummary(true, currentDebtOf(request.getProgramEnrollment().getStudent().getId()));
    }

    private StudentDebtView currentDebtOf(Long studentId) {
        if (!graduationDebtGate.mustCheckDebt() || !invoiceQueryService.hasDebt(studentId)) {
            return StudentDebtView.none();
        }
        return invoiceQueryService.debtOf(studentId);
    }

    private PendingDebtException pendingDebtOf(Long studentId, StudentDebtView debt) {
        return new PendingDebtException(studentId, debt.totalDue(), debt.installmentCount());
    }
}
