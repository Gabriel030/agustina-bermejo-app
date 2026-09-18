package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.CollectionsPolicy;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.MonetaryAdjustmentValue;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
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
    public void process(Long invoiceId, CollectionsPolicy policy) {
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        invoice.markOverdue(new MonetaryAdjustmentValue.PercentageAmount(policy.getSurchargePercentage()), "Mora automática");
        invoiceRepository.save(invoice);
    }
}
