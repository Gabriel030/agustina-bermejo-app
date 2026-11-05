package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class InvoiceFollowUpQueries {
    private static final List<InvoicePaymentStatus> UNPAID_STATUSES = List.of(InvoicePaymentStatus.PENDING, InvoicePaymentStatus.OVERDUE,
            InvoicePaymentStatus.PAYMENT_UNDER_REVIEW);

    private final InvoiceRepository invoiceRepository;

    public InvoiceFollowUpQueries(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    public Optional<Invoice> findInvoice(Long invoiceId) {
        return invoiceRepository.findById(invoiceId);
    }

    public boolean hasUnpaidIssuedDocumentOverdue(Long studentCurrentAccountId, LocalDate today) {
        return invoiceRepository.existsUnpaidIssuedDocumentDueBefore(studentCurrentAccountId, UNPAID_STATUSES, today);
    }

    public long countOverdueOfCourseEnrollment(Long courseEnrollmentId) {
        return invoiceRepository.countByCourseEnrollmentIdAndPaymentStatus(courseEnrollmentId, InvoicePaymentStatus.OVERDUE);
    }

    public long countOverdueOfProgramEnrollment(Long programEnrollmentId) {
        return invoiceRepository.countByProgramEnrollmentIdAndPaymentStatus(programEnrollmentId, InvoicePaymentStatus.OVERDUE);
    }
}
