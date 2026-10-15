package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InvoiceAllocationQueryService {

    private final InvoiceRepository invoiceRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Invoice> listOpenInvoicesOfAccount(Long studentCurrentAccountId) {
        return invoiceRepository.findOpenIssuedInvoicesByAccountOrderedForAllocation(studentCurrentAccountId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Invoice lockInvoice(Long invoiceId) {
        return invoiceRepository.findByIdForUpdate(invoiceId).orElseThrow();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Invoice> findInvoice(Long invoiceId) {
        return invoiceRepository.findById(invoiceId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long countInvoicesOfCourseEnrollmentWithPaymentStatus(Long courseEnrollmentId, InvoicePaymentStatus paymentStatus) {
        return invoiceRepository.countByCourseEnrollmentIdAndPaymentStatus(courseEnrollmentId, paymentStatus);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long countInvoicesOfProgramEnrollmentWithPaymentStatus(Long programEnrollmentId, InvoicePaymentStatus paymentStatus) {
        return invoiceRepository.countByProgramEnrollmentIdAndPaymentStatus(programEnrollmentId, paymentStatus);
    }
}
