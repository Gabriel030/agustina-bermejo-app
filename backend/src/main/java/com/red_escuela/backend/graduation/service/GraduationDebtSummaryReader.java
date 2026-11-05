package com.red_escuela.backend.graduation.service;

import com.red_escuela.backend.billing.api.StudentDebtView;
import com.red_escuela.backend.billing.service.InvoiceQueryService;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GraduationDebtSummaryReader {

    private final GraduationRequestFinder graduationRequestFinder;
    private final GraduationDebtGate graduationDebtGate;
    private final InvoiceQueryService invoiceQueryService;

    public GraduationDebtSummaryReader(GraduationRequestFinder graduationRequestFinder, GraduationDebtGate graduationDebtGate,
                                       InvoiceQueryService invoiceQueryService) {
        this.graduationRequestFinder = graduationRequestFinder;
        this.graduationDebtGate = graduationDebtGate;
        this.invoiceQueryService = invoiceQueryService;
    }

    @Transactional(readOnly = true)
    public GraduationDebtSummary summaryOf(Long graduationRequestId) {
        GraduationRequest request = graduationRequestFinder.findOrThrow(graduationRequestId);
        if (!graduationDebtGate.mustCheckDebt()) {
            return new GraduationDebtSummary(false, StudentDebtView.none());
        }
        Long studentId = request.getProgramEnrollment().getStudent().getId();
        return new GraduationDebtSummary(true, invoiceQueryService.debtOf(studentId));
    }
}
