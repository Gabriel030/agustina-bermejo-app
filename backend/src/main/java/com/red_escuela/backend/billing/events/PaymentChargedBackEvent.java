package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentChargedBackEvent(UUID eventId, String correlationId, Instant occurredAt, Long paymentId, Long chargebackId, Long studentCurrentAccountId,
                                      LocalDate chargedBackOn, NonNegativeMoney amount, String reference) implements DeduplicableDomainEvent {

    public static PaymentChargedBackEvent chargedBackFor(Long paymentId, Long chargebackId, Long studentCurrentAccountId, LocalDate chargedBackOn,
                                                         NonNegativeMoney amount, String reference) {
        return new PaymentChargedBackEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), paymentId, chargebackId, studentCurrentAccountId,
                chargedBackOn, amount, reference);
    }
}
