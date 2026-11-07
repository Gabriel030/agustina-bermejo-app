package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.policy.OutstandingDebtFacts;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WithdrawnEnrollmentDebtFactsLoader {

    private final InvoiceRepository invoiceRepository;

    public WithdrawnEnrollmentDebtFactsLoader(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional(readOnly = true)
    public OutstandingDebtFacts loadFactsOf(Long studentId) {
        return new OutstandingDebtFacts(studentId,
                invoiceRepository.existsOpenInvoiceOfWithdrawnEnrollmentByStudentId(studentId),
                invoiceRepository.existsOpenInvoiceOfWithdrawnEnrollmentInPendingRequestByStudentId(studentId));
    }
}
