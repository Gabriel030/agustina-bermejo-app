package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import java.time.LocalDate;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.entity.LatePaymentSettings;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceOverdueRowProcessor {
    private final InvoiceRepository invoiceRepository;

    public InvoiceOverdueRowProcessor(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(Long invoiceId, LocalDate businessDate) {
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        if (invoice.getPaymentStatus() == InvoicePaymentStatus.PENDING) {
            invoice.markInvoiceAsOverdue(businessDate, new StatusChangeActor(null, "InvoiceOverdueSweep"));
        }
    }
}
