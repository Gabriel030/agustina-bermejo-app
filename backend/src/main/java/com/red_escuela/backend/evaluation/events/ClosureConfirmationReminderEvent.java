package com.red_escuela.backend.evaluation.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.util.List;

public record ClosureConfirmationReminderEvent(String correlationId, Instant occurredAt, String sectionKind, Long sectionId,
                                               String sectionLabel, List<Long> instructorPersonIds, int daysAfterTermEnd) implements DomainEvent {

    public static ClosureConfirmationReminderEvent of(String sectionKind, Long sectionId, String sectionLabel,
            List<Long> instructorPersonIds, int daysAfterTermEnd) {
        return new ClosureConfirmationReminderEvent(CorrelationIds.current(), ApplicationTime.instant(), sectionKind, sectionId,
                sectionLabel, instructorPersonIds, daysAfterTermEnd);
    }
}
