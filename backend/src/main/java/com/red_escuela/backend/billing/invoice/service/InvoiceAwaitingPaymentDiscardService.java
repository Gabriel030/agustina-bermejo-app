package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceAwaitingPaymentDiscardService {
    private final InvoiceRepository invoiceRepository;

    public InvoiceAwaitingPaymentDiscardService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Invoice lockInvoiceAwaitingPayment(Long invoiceId) {
        return invoiceRepository.findByIdForUpdate(invoiceId).orElseThrow();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void discardInvoiceAwaitingPayment(Invoice invoice, String reason, StatusChangeActor actor) {
        invoice.discardDraftInvoice(reason, actor);
        invoiceRepository.flush();
    }
}
