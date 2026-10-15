package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentApprovedEvent(UUID eventId, String correlationId, Instant occurredAt, Long paymentId, Long studentCurrentAccountId, LocalDate paidOn,
                                   NonNegativeMoney amount) implements DeduplicableDomainEvent {

    public static PaymentApprovedEvent approvedFor(Long paymentId, Long studentCurrentAccountId, LocalDate paidOn, NonNegativeMoney amount) {
        return new PaymentApprovedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), paymentId, studentCurrentAccountId, paidOn, amount);
    }
}
