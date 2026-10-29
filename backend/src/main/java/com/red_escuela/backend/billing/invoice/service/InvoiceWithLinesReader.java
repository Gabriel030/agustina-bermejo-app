package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class InvoiceWithLinesReader {
    private final InvoiceRepository invoices;

    public InvoiceWithLinesReader(InvoiceRepository invoices) {
        this.invoices = invoices;
    }

    @Transactional(readOnly = true)
    public Optional<Invoice> findInvoiceWithLines(Long invoiceId) {
        return invoices.findInvoiceWithLinesById(invoiceId);
    }
}
