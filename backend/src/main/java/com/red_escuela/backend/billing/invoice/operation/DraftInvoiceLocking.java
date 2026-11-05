package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;

import java.util.List;
import java.util.SortedSet;

final class DraftInvoiceLocking {

    private DraftInvoiceLocking() {
    }

    static List<Invoice> lockPendingDraftsOfRun(InvoiceRepository invoiceRepository, Long monthlyInvoiceRunId, SortedSet<Long> orderedInvoiceIds) {
        return orderedInvoiceIds.stream().map(id -> {
            Invoice invoice = invoiceRepository.findByIdForUpdate(id).orElseThrow(() -> new InvoiceNotFoundException(id));
            invoice.requirePendingDraftOfRun(monthlyInvoiceRunId);
            return invoice;
        }).toList();
    }
}
