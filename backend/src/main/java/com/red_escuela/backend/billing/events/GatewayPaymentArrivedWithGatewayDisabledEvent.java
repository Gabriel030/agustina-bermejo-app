package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record GatewayPaymentArrivedWithGatewayDisabledEvent(UUID eventId, String correlationId, Instant occurredAt, Long paymentId, Long reportedInvoiceId,
                                                            String gateway, BigDecimal amount, String currency) implements DeduplicableDomainEvent {

    public static GatewayPaymentArrivedWithGatewayDisabledEvent arrivedFor(Long paymentId, Long reportedInvoiceId, String gateway, BigDecimal amount,
                                                                           String currency) {
        return new GatewayPaymentArrivedWithGatewayDisabledEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), paymentId,
                reportedInvoiceId, gateway, amount, currency);
    }
}
