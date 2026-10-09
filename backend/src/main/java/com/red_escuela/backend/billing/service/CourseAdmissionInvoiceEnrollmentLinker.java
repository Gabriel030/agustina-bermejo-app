package com.red_escuela.backend.billing.service;

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

    @EventListener
    @Transactional
    public void linkInvoiceToConfirmedCourseEnrollment(CourseEnrollmentConfirmedByAdmissionPaymentEvent event) {
        invoiceRepository.findInvoiceWithLinesById(event.invoiceId()).orElseThrow(() -> new InvoiceNotFoundException(event.invoiceId()))
                .assignInvoiceToCourseEnrollment(event.courseEnrollmentId());
    }
}
