package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.invoice.service.InvoiceNumberGenerator;
import com.red_escuela.backend.billing.invoice.service.StudentSnapshotFinder;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;

@Component
public class AwaitingPaymentInvoiceIssuer {
    private final InvoiceRepository invoiceRepository;
    private final InvoiceNumberGenerator numberGenerator;
    private final StudentSnapshotFinder snapshotFinder;
    private final ApplicationEventPublisher eventPublisher;

    public AwaitingPaymentInvoiceIssuer(InvoiceRepository invoiceRepository, InvoiceNumberGenerator numberGenerator,
                                        StudentSnapshotFinder snapshotFinder, ApplicationEventPublisher eventPublisher) {
        this.invoiceRepository = invoiceRepository;
        this.numberGenerator = numberGenerator;
        this.snapshotFinder = snapshotFinder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void issueInvoiceAwaitingPayment(Invoice invoice, Long studentId, LocalDate issueDate, StatusChangeActor actor) {
        Objects.requireNonNull(invoice, "invoice");
        if (invoice.getDocumentStatus() != InvoiceDocumentStatus.AWAITING_PAYMENT) {
            throw new IllegalStateException("Invoice " + invoice.getId() + " is not awaiting payment");
        }
        invoice.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), issueDate, issueDate, snapshotFinder.findSnapshotOfStudent(studentId),
                actor);
        invoiceRepository.flush();
        RecordedInvoiceEventTranslator.translateRecordedEventsOf(invoice).forEach((DomainEvent event) -> eventPublisher.publishEvent(event));
    }
}
