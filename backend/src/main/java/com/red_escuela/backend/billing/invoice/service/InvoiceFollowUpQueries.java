package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class InvoiceFollowUpQueries {
    private final InvoiceRepository invoiceRepository;

    public InvoiceFollowUpQueries(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    public Optional<Invoice> findInvoice(Long invoiceId) {
        return invoiceRepository.findById(invoiceId);
    }

    public long countOverdueOfCourseEnrollment(Long courseEnrollmentId) {
        return invoiceRepository.countByCourseEnrollmentIdAndPaymentStatus(courseEnrollmentId, InvoicePaymentStatus.OVERDUE);
    }

    public long countOverdueOfProgramEnrollment(Long programEnrollmentId) {
        return invoiceRepository.countByProgramEnrollmentIdAndPaymentStatus(programEnrollmentId, InvoicePaymentStatus.OVERDUE);
    }
}
