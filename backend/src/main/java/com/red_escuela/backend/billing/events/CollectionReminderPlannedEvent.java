package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CollectionReminderPlannedEvent(UUID eventId, String correlationId, Instant occurredAt, Long reminderId, Long studentPersonId, LocalDate businessDate,
                                             List<PlannedReminderMessage> inAppMessages, List<PlannedReminderMessage> emailMessages) implements DeduplicableDomainEvent {

    public CollectionReminderPlannedEvent {
        inAppMessages = List.copyOf(inAppMessages);
        emailMessages = List.copyOf(emailMessages);
    }

    public static CollectionReminderPlannedEvent plannedFor(Long reminderId, Long studentPersonId, LocalDate businessDate, List<PlannedReminderMessage> inAppMessages,
                                                            List<PlannedReminderMessage> emailMessages) {
        return new CollectionReminderPlannedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), reminderId, studentPersonId, businessDate,
                inAppMessages, emailMessages);
    }
}
