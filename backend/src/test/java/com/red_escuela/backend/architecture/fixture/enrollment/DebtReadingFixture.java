package com.red_escuela.backend.architecture.fixture.enrollment;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;

public class DebtReadingFixture {

    private final InvoiceRepository invoiceRepository;

    public DebtReadingFixture(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    public boolean studentIsInDebt(Long studentId) {
        return invoiceRepository.existsByStudentIdAndPaymentStatus(studentId, InvoicePaymentStatus.OVERDUE);
    }
}
