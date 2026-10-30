package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

public record MonthlyInvoiceItemRetriesExhaustedEvent(UUID eventId, String correlationId, Instant occurredAt, Long monthlyInvoiceRunItemId, Long monthlyInvoiceRunId,
                                                      String enrollmentType, Long enrollmentId, String errorCode) implements DeduplicableDomainEvent {

    public static MonthlyInvoiceItemRetriesExhaustedEvent exhaustedFor(Long monthlyInvoiceRunItemId, Long monthlyInvoiceRunId, String enrollmentType, Long enrollmentId,
                                                                       String errorCode) {
        UUID oneIdentityPerItem = UUID.nameUUIDFromBytes(("MonthlyInvoiceItemRetriesExhausted:" + monthlyInvoiceRunItemId).getBytes(StandardCharsets.UTF_8));
        return new MonthlyInvoiceItemRetriesExhaustedEvent(oneIdentityPerItem, CorrelationIds.current(), ApplicationTime.instant(), monthlyInvoiceRunItemId,
                monthlyInvoiceRunId, enrollmentType, enrollmentId, errorCode);
    }
}
