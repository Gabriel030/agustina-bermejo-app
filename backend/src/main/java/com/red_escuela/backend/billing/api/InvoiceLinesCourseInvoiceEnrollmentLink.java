package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceLinesCourseInvoiceEnrollmentLink implements CourseInvoiceEnrollmentLink {

    private final InvoiceRepository invoiceRepository;

    public InvoiceLinesCourseInvoiceEnrollmentLink(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Override
    @Transactional
    public void linkInvoiceToCourseEnrollment(Long invoiceId, Long courseEnrollmentId) {
        invoiceRepository.findInvoiceWithLinesById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId))
                .assignInvoiceToCourseEnrollment(courseEnrollmentId);
    }
}
