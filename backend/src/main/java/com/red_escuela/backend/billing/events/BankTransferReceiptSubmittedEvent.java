package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BankTransferReceiptSubmittedEvent(UUID eventId, String correlationId, Instant occurredAt, Long receiptId, Long paymentId,
                                                Long studentCurrentAccountId, NonNegativeMoney declaredAmount, List<Long> invoiceIds)
        implements DeduplicableDomainEvent {

    public BankTransferReceiptSubmittedEvent {
        invoiceIds = List.copyOf(invoiceIds);
    }

    public static BankTransferReceiptSubmittedEvent submittedFor(Long receiptId, Long paymentId, Long studentCurrentAccountId, NonNegativeMoney declaredAmount,
                                                                 List<Long> invoiceIds) {
        return new BankTransferReceiptSubmittedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), receiptId, paymentId,
                studentCurrentAccountId, declaredAmount, invoiceIds);
    }
}
