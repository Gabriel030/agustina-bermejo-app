package com.red_escuela.backend.billing.run.dto;

import com.red_escuela.backend.billing.invoice.domain.Invoice;

import java.util.Collection;
import java.util.List;

public record DraftInvoiceBatchResponse(List<Long> invoiceIds, int count) {

    public static DraftInvoiceBatchResponse of(Collection<Invoice> invoices) {
        List<Long> ids = invoices.stream().map(Invoice::getId).sorted().toList();
        return new DraftInvoiceBatchResponse(ids, ids.size());
    }
}
