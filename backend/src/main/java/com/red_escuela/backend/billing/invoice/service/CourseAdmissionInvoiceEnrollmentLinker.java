package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.RequiresModule;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.enrollment.events.CourseEnrollmentConfirmedByAdmissionPaymentEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CourseAdmissionInvoiceEnrollmentLinker {

    private final InvoiceRepository invoiceRepository;

    public CourseAdmissionInvoiceEnrollmentLinker(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
    @EventListener
    @Transactional
    public void linkInvoiceToConfirmedCourseEnrollment(CourseEnrollmentConfirmedByAdmissionPaymentEvent event) {
        invoiceRepository.findInvoiceWithLinesById(event.invoiceId()).orElseThrow(() -> new InvoiceNotFoundException(event.invoiceId()))
                .assignInvoiceToCourseEnrollment(event.courseEnrollmentId());
    }
}
