package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceAwaitingPaymentRegistrationService {
    private final InvoiceRepository invoiceRepository;

    public InvoiceAwaitingPaymentRegistrationService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Invoice saveInvoiceAwaitingPayment(Invoice invoice) {
        return invoiceRepository.saveAndFlush(invoice);
    }
}
