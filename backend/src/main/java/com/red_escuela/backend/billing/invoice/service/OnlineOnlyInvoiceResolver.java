package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.OnlineOnlyInvoiceSelection;
import com.red_escuela.backend.enrollment.usecase.OnlineOnlyEnrollmentLookup;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;

@Service
public class OnlineOnlyInvoiceResolver {

    private final OnlineOnlyEnrollmentLookup enrollmentLookup;

    public OnlineOnlyInvoiceResolver(OnlineOnlyEnrollmentLookup enrollmentLookup) {
        this.enrollmentLookup = enrollmentLookup;
    }

    public Set<Long> findInvoiceIdsOfOnlineOnlyOfferings(Collection<Invoice> invoices) {
        return OnlineOnlyInvoiceSelection.invoiceIdsOfOnlineOnlyOfferings(invoices,
                enrollmentLookup.findOnlineOnlyProgramEnrollmentIds(OnlineOnlyInvoiceSelection.programEnrollmentIdsOf(invoices)),
                enrollmentLookup.findOnlineOnlyCourseEnrollmentIds(OnlineOnlyInvoiceSelection.courseEnrollmentIdsOf(invoices)));
    }
}
