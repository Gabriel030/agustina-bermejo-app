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
        if (graduationDebtGate.mustCheckDebt() && invoiceQueryService.hasDebt(studentId)) {
            throw pendingDebtOf(studentId, invoiceQueryService.debtOf(studentId));
        }
    }

    public void requireNoDebtNotifying(GraduationRequest request, Long studentId) {
        if (graduationDebtGate.mustCheckDebt() && invoiceQueryService.hasDebt(studentId)) {
            StudentDebtView debt = invoiceQueryService.debtOf(studentId);
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
        return new GraduationDebtSummary(true, invoiceQueryService.debtOf(request.getProgramEnrollment().getStudent().getId()));
    }

    private PendingDebtException pendingDebtOf(Long studentId, StudentDebtView debt) {
        return new PendingDebtException(studentId, debt.totalDue(), debt.installmentCount());
    }
}
