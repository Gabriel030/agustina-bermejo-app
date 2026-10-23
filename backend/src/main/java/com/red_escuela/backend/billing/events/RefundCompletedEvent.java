package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RefundCompletedEvent(UUID eventId, String correlationId, Instant occurredAt, Long refundId, Long refundRequestId, Long paymentId,
                                   Long studentCurrentAccountId, NonNegativeMoney amount, LocalDate completedOn, String reference) implements DeduplicableDomainEvent {

    public static RefundCompletedEvent completedFor(Long refundId, Long refundRequestId, Long paymentId, Long studentCurrentAccountId, NonNegativeMoney amount,
                                                    LocalDate completedOn, String reference) {
        return new RefundCompletedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), refundId, refundRequestId, paymentId,
                studentCurrentAccountId, amount, completedOn, reference);
    }
}
