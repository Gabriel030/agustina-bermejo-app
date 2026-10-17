package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.issuing.DiscountCandidateOrigin;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;

import java.util.List;

public interface InvoiceDiscountCandidateStrategy {

    DiscountCandidateOrigin origin();

    List<InvoiceDiscountCandidate> findDiscountCandidatesForInvoice(InvoiceIssuingContext context);

    default List<InvoiceDiscountCandidate> findDiscountCandidatesUnderEnrollmentLock(InvoiceIssuingContext context) {
        return findDiscountCandidatesForInvoice(context);
    }

    default void markDiscountAsApplied(InvoiceDiscountCandidate winner) {
    }
}
