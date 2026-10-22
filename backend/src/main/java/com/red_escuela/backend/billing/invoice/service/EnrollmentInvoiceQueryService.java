package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class EnrollmentInvoiceQueryService {
    private final InvoiceRepository invoiceRepository;

    public EnrollmentInvoiceQueryService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional(readOnly = true)
    public List<Long> findAccountIdsOfEnrollment(Long programEnrollmentId, Long courseEnrollmentId) {
        return invoiceRepository.findAccountIdsOfEnrollment(programEnrollmentId, courseEnrollmentId);
    }

    @Transactional(readOnly = true)
    public Optional<Long> findAccountIdOfInvoice(Long invoiceId) {
        return invoiceRepository.findStudentCurrentAccountIdByInvoiceId(invoiceId);
    }

    @Transactional(readOnly = true)
    public Optional<Invoice> findInvoiceWithLines(Long invoiceId) {
        return invoiceRepository.findInvoiceWithLinesById(invoiceId);
    }

    @Transactional(readOnly = true)
    public List<Invoice> findInvoicesOfEnrollment(Long programEnrollmentId, Long courseEnrollmentId) {
        return programEnrollmentId != null ? invoiceRepository.findByProgramEnrollmentId(programEnrollmentId) : invoiceRepository.findByCourseEnrollmentId(courseEnrollmentId);
    }

    @Transactional(readOnly = true)
    public Set<Long> findInvoiceIdsWithPendingAuthorizationRequest(Collection<Long> invoiceIds) {
        if (invoiceIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(invoiceRepository.findInvoiceIdsWithPendingAuthorizationRequest(invoiceIds));
    }

    @Transactional
    public void discardDraftInvoices(Collection<Long> invoiceIds, String reason, StatusChangeActor actor) {
        invoiceIds.forEach(id -> invoiceRepository.findById(id).orElseThrow().discardDraftInvoice(reason, actor));
    }
}
