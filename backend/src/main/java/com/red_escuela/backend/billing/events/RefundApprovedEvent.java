package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.util.UUID;

public record RefundApprovedEvent(UUID eventId, String correlationId, Instant occurredAt, Long refundId, Long refundRequestId, Long paymentId,
                                  Long studentCurrentAccountId, NonNegativeMoney amount) implements DeduplicableDomainEvent {

    public static RefundApprovedEvent approvedFor(Long refundId, Long refundRequestId, Long paymentId, Long studentCurrentAccountId, NonNegativeMoney amount) {
        return new RefundApprovedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), refundId, refundRequestId, paymentId,
                studentCurrentAccountId, amount);
    }
}
