package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;

import java.util.List;

public interface InvoiceDiscountCandidateStrategy {

    DiscountType discountType();

    List<InvoiceDiscountCandidate> findDiscountCandidatesForInvoice(InvoiceIssuingContext context);

    default void markDiscountAsApplied(InvoiceDiscountCandidate winner) {
    }
}
