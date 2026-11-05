package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.event.InvoiceDiscardedEvent;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Service
public class EnrollmentInvoiceQueryService {
    private final InvoiceRepository invoiceRepository;
    private final ApplicationEventPublisher eventPublisher;

    public EnrollmentInvoiceQueryService(InvoiceRepository invoiceRepository, ApplicationEventPublisher eventPublisher) {
        this.invoiceRepository = invoiceRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<Long> findAccountIdsOfEnrollment(Long programEnrollmentId, Long courseEnrollmentId) {
        return invoiceRepository.findAccountIdsOfEnrollment(programEnrollmentId, courseEnrollmentId);
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
        invoiceIds.forEach(id -> {
            Invoice draft = invoiceRepository.findById(id).orElseThrow();
            draft.discardDraftInvoice(reason, actor);
            draft.pullRecordedDomainEvents().stream().filter(InvoiceDiscardedEvent.class::isInstance).forEach(eventPublisher::publishEvent);
        });
    }
}
