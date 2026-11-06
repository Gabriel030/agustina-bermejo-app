package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
public class StoredInvoiceLoader {
    private final InvoiceRepository invoiceRepository;

    public StoredInvoiceLoader(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    public List<Invoice> loadInvoices(Collection<Long> invoiceIds) {
        return invoiceRepository.findByIdIn(invoiceIds);
    }
}
