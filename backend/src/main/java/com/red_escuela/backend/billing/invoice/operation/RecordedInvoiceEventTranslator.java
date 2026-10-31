package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoiceIssuedDomainEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoiceIssuedEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidEvent;
import com.red_escuela.backend.shared.event.DomainEvent;

import java.util.List;
import java.util.Optional;

final class RecordedInvoiceEventTranslator {

    private RecordedInvoiceEventTranslator() {
    }

    static List<DomainEvent> translateRecordedEventsOf(Invoice invoice) {
        return invoice.pullRecordedDomainEvents().stream().flatMap(event -> toPublishedEvent(event, invoice).stream()).toList();
    }

    private static Optional<DomainEvent> toPublishedEvent(Object recorded, Invoice invoice) {
        return switch (recorded) {
            case InvoiceIssuedEvent issued -> Optional.of(InvoiceIssuedDomainEvent.issuedFor(issued.invoiceId(), issued.studentCurrentAccountId(),
                    invoice.getInvoiceNumber().value(), issued.invoiceType().name(), issued.total(), issued.issueDate(), issued.dueDate()));
            case InvoicePaidEvent paid when paid.cause() != InvoicePaymentEvent.FULL_PAYMENT_ALLOCATED -> Optional.of(InvoicePaidDomainEvent.paidFor(paid.invoiceId(), paid.studentCurrentAccountId(), paid.cause().name(),
                    paid.businessDate()));
            default -> Optional.empty();
        };
    }
}
