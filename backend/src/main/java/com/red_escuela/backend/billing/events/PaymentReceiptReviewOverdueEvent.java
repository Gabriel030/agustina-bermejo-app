package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentReceiptReviewOverdueEvent(UUID eventId, String correlationId, Instant occurredAt, Long receiptId, Long paymentId, Long invoiceId,
                                               LocalDate uploadedOn, int pendingBusinessDays) implements DeduplicableDomainEvent {

    public static PaymentReceiptReviewOverdueEvent overdueFor(Long receiptId, Long paymentId, Long invoiceId, LocalDate uploadedOn, int pendingBusinessDays) {
        return new PaymentReceiptReviewOverdueEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), receiptId, paymentId, invoiceId,
                uploadedOn, pendingBusinessDays);
    }
}
