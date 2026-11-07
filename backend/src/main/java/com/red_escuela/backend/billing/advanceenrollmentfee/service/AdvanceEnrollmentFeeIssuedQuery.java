package com.red_escuela.backend.billing.advanceenrollmentfee.service;

import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdvanceEnrollmentFeeIssuedQuery {
    private final InvoiceRepository invoiceRepository;

    public AdvanceEnrollmentFeeIssuedQuery(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional(readOnly = true)
    public boolean hasIssuedFor(Long templateId) {
        return invoiceRepository.existsLiveAdvanceEnrollmentFeeByTemplateId(templateId);
    }
}
