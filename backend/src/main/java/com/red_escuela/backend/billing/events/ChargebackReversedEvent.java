package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ChargebackReversedEvent(UUID eventId, String correlationId, Instant occurredAt, Long paymentId, Long chargebackId, Long studentCurrentAccountId,
                                      LocalDate reversedOn, NonNegativeMoney amount) implements DeduplicableDomainEvent {

    public static ChargebackReversedEvent reversedFor(Long paymentId, Long chargebackId, Long studentCurrentAccountId, LocalDate reversedOn, NonNegativeMoney amount) {
        return new ChargebackReversedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), paymentId, chargebackId, studentCurrentAccountId,
                reversedOn, amount);
    }
}
