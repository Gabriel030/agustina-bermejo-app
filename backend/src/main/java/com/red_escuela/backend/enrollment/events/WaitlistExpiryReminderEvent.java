package com.red_escuela.backend.enrollment.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDate;

public record WaitlistExpiryReminderEvent(String correlationId, Instant occurredAt, Long enrollmentId, Long studentPersonId,
        String queueName, LocalDate closesOn) implements DomainEvent {

    public static WaitlistExpiryReminderEvent of(Long enrollmentId, Long studentPersonId, String queueName, LocalDate closesOn) {
        return new WaitlistExpiryReminderEvent(CorrelationIds.current(), ApplicationTime.instant(), enrollmentId, studentPersonId,
                queueName, closesOn);
    }
}
