package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.util.UUID;

public record PaymentReceiptAppliedToOlderDebtEvent(UUID eventId, String correlationId, Instant occurredAt, Long receiptId, Long invoiceId,
                                                    Long payerPersonId, String period) implements DeduplicableDomainEvent {

    public static PaymentReceiptAppliedToOlderDebtEvent appliedFor(Long receiptId, Long invoiceId, Long payerPersonId, String period) {
        return new PaymentReceiptAppliedToOlderDebtEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), receiptId, invoiceId,
                payerPersonId, period);
    }
}
