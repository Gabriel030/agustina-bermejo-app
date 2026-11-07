package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record AnnualUpfrontDeadlineNoticePlannedEvent(UUID eventId, String correlationId, Instant occurredAt, Long requestId, Long invoiceId, Long studentPersonId,
                                                      LocalDateTime paymentDeadline, Money total, LocalDate businessDate) implements DeduplicableDomainEvent {

    public static AnnualUpfrontDeadlineNoticePlannedEvent plannedFor(Long requestId, Long invoiceId, Long studentPersonId, LocalDateTime paymentDeadline, Money total,
                                                                     LocalDate businessDate) {
        UUID eventIdOfTheRequest = UUID.nameUUIDFromBytes(("annual-upfront-deadline-notice:" + requestId).getBytes(StandardCharsets.UTF_8));
        return new AnnualUpfrontDeadlineNoticePlannedEvent(eventIdOfTheRequest, CorrelationIds.current(), ApplicationTime.instant(), requestId, invoiceId, studentPersonId,
                paymentDeadline, total, businessDate);
    }
}
