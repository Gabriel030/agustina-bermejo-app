package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentLeftAsAccountCreditEvent(UUID eventId, String correlationId, Instant occurredAt, Long paymentId, Long reportedInvoiceId,
                                              Long studentPersonId, BigDecimal amount, String currency) implements DeduplicableDomainEvent {

    public static PaymentLeftAsAccountCreditEvent leftFor(Long paymentId, Long reportedInvoiceId, Long studentPersonId, BigDecimal amount, String currency) {
        return new PaymentLeftAsAccountCreditEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), paymentId, reportedInvoiceId,
                studentPersonId, amount, currency);
    }
}
