package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.exception.DraftNotPendingException;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;

import java.util.List;
import java.util.SortedSet;

final class DraftInvoiceLocking {

    private DraftInvoiceLocking() {
    }

    static List<Invoice> lockPendingDraftsOfRun(InvoiceRepository invoiceRepository, Long monthlyInvoiceRunId, SortedSet<Long> orderedInvoiceIds) {
        return orderedInvoiceIds.stream().map(id -> invoiceRepository.findByIdForUpdate(id)
                .filter(invoice -> monthlyInvoiceRunId.equals(invoice.getMonthlyInvoiceRunId()) && invoice.getDocumentStatus() == InvoiceDocumentStatus.DRAFT)
                .orElseThrow(() -> new DraftNotPendingException(id))).toList();
    }
}
