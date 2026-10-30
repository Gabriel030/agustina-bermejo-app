package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.events.AdmissionInvoicePaidEvent;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentEvent;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import com.red_escuela.backend.billing.invoice.service.InvoiceFollowUpQueries;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class InvoicePaidFollowUp {
    private final InvoiceFollowUpQueries invoiceQueries;
    private final ApplicationEventPublisher eventPublisher;

    public InvoicePaidFollowUp(InvoiceFollowUpQueries invoiceQueries, ApplicationEventPublisher eventPublisher) {
        this.invoiceQueries = invoiceQueries;
        this.eventPublisher = eventPublisher;
    }

    @EventListener
    public void followUpInvoicePaidByAllocation(InvoicePaidDomainEvent event) {
        if (!InvoicePaymentEvent.FULL_PAYMENT_ALLOCATED.name().equals(event.cause())) {
            return;
        }
        invoiceQueries.findInvoice(event.invoiceId()).ifPresent(this::publishAdmissionPaidIfApplicable);
    }

    private void publishAdmissionPaidIfApplicable(Invoice invoice) {
        if (invoice.getIssuingReason() != null && invoice.getIssuingReason().isAdmission() && invoice.getPaymentStatus() == InvoicePaymentStatus.PAID) {
            eventPublisher.publishEvent(AdmissionInvoicePaidEvent.paidFor(invoice.getId()));
        }
    }
}
