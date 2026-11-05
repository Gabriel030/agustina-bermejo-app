package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.exception.OutstandingDebtBlocksReenrollmentException;
import com.red_escuela.backend.billing.invoice.policy.OutstandingDebtFacts;
import com.red_escuela.backend.billing.api.EnrollmentDebtGuard;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceDebtEnrollmentGuard implements EnrollmentDebtGuard {

    private final InvoiceRepository invoiceRepository;
    private final PolicySet<OutstandingDebtFacts> reenrollmentDebtPolicySet;

    public InvoiceDebtEnrollmentGuard(InvoiceRepository invoiceRepository, PolicySet<OutstandingDebtFacts> reenrollmentDebtPolicySet) {
        this.invoiceRepository = invoiceRepository;
        this.reenrollmentDebtPolicySet = reenrollmentDebtPolicySet;
    }

    @Override
    @Transactional(readOnly = true)
    public void verifyNoOutstandingDebt(Long studentId) {
        OutstandingDebtFacts facts = new OutstandingDebtFacts(studentId,
                invoiceRepository.existsByStudentIdAndPaymentStatus(studentId, InvoicePaymentStatus.OVERDUE),
                invoiceRepository.existsOpenInvoiceOfWithdrawnEnrollmentByStudentId(studentId),
                invoiceRepository.existsOpenInvoiceOfWithdrawnEnrollmentInPendingRequestByStudentId(studentId));
        reenrollmentDebtPolicySet.evaluateAllRules(facts).throwIfDenied(OutstandingDebtBlocksReenrollmentException::new);
    }
}
