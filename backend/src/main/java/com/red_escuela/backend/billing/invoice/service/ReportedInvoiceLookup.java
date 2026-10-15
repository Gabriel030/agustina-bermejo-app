package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import org.springframework.stereotype.Service;

@Service
public class ReportedInvoiceLookup {
    private final InvoiceRepository invoiceRepository;

    public ReportedInvoiceLookup(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    public ReportedInvoice findReportedInvoice(Long invoiceId) {
        Long accountId = invoiceRepository.findStudentCurrentAccountIdByInvoiceId(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        boolean programAdmission = invoice.getIssuingReason() != null && invoice.getIssuingReason().isProgramAdmission();
        Long ownerPersonId = invoiceRepository.findOwnerPersonByInvoiceId(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId)).getId();
        return new ReportedInvoice(invoiceId, accountId, programAdmission, ownerPersonId);
    }

    public record ReportedInvoice(Long invoiceId, Long studentCurrentAccountId, boolean programAdmission, Long ownerPersonId) {
    }
}
