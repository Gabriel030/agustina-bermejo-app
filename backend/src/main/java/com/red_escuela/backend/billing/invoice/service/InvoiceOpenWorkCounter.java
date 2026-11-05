package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class InvoiceOpenWorkCounter {

    public record InvoiceOpenWork(long open, long overdue, long drafts, long awaitingPayment, long underPaymentReview) {
    }

    private static final Set<InvoicePaymentStatus> OPEN_PAYMENT_STATUSES =
            Set.of(InvoicePaymentStatus.PENDING, InvoicePaymentStatus.OVERDUE, InvoicePaymentStatus.PAYMENT_UNDER_REVIEW);

    private final InvoiceRepository invoiceRepository;

    public InvoiceOpenWorkCounter(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional(readOnly = true)
    public InvoiceOpenWork countOpenWork() {
        return new InvoiceOpenWork(
                invoiceRepository.countByDocumentStatusAndPaymentStatusIn(InvoiceDocumentStatus.ISSUED, OPEN_PAYMENT_STATUSES),
                invoiceRepository.countByDocumentStatusAndPaymentStatusIn(InvoiceDocumentStatus.ISSUED, Set.of(InvoicePaymentStatus.OVERDUE)),
                invoiceRepository.countByDocumentStatus(InvoiceDocumentStatus.DRAFT),
                invoiceRepository.countByDocumentStatus(InvoiceDocumentStatus.AWAITING_PAYMENT),
                invoiceRepository.countByDocumentStatusAndPaymentStatusIn(InvoiceDocumentStatus.ISSUED, Set.of(InvoicePaymentStatus.PAYMENT_UNDER_REVIEW)));
    }
}
