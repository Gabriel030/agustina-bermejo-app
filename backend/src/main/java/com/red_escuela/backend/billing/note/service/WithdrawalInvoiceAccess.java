package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.service.EnrollmentInvoiceQueryService;
import com.red_escuela.backend.shared.billingterms.ProgramTermStartLookup;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class WithdrawalInvoiceAccess {
    private final EnrollmentInvoiceQueryService invoiceQueries;
    private final ProgramTermStartLookup termStarts;

    public WithdrawalInvoiceAccess(EnrollmentInvoiceQueryService invoiceQueries, ProgramTermStartLookup termStarts) {
        this.invoiceQueries = invoiceQueries;
        this.termStarts = termStarts;
    }

    public List<Invoice> findInvoicesOfEnrollment(Long programEnrollmentId, Long courseEnrollmentId) {
        return invoiceQueries.findInvoicesOfEnrollment(programEnrollmentId, courseEnrollmentId);
    }

    public Set<Long> findInvoiceIdsWithPendingAuthorizationRequest(Collection<Long> invoiceIds) {
        return invoiceQueries.findInvoiceIdsWithPendingAuthorizationRequest(invoiceIds);
    }

    public Optional<LocalDate> findTermStartOfTemplate(Long templateId) {
        return termStarts.findTermStartOfTemplate(templateId);
    }

    public void discardDraftInvoices(Collection<Long> invoiceIds, String reason, StatusChangeActor actor) {
        invoiceQueries.discardDraftInvoices(invoiceIds, reason, actor);
    }
}
