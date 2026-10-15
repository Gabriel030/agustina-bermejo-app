package com.red_escuela.backend.billing.invoice.domain.issuing;

import com.red_escuela.backend.billing.invoice.domain.InvoiceType;

import java.util.List;

public record DiscountContest(InvoiceType invoiceType, InvoiceDiscountCandidate candidate, List<InvoiceDiscountCandidate> rivals) {

    public DiscountContest {
        rivals = List.copyOf(rivals);
    }
}
