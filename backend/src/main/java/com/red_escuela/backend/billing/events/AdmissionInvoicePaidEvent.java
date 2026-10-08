package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record AdmissionInvoicePaidEvent(String correlationId, Instant occurredAt, Long invoiceId) implements DomainEvent {

    public static AdmissionInvoicePaidEvent paidFor(Long invoiceId) {
        return new AdmissionInvoicePaidEvent(CorrelationIds.current(), ApplicationTime.instant(), invoiceId);
    }
}
