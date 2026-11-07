package com.red_escuela.backend.evaluation.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record GradeCorrectedEvent(String correlationId, Instant occurredAt, Long gradeItemId, Long studentPersonId,
                                  String sectionName, String gradeTypeLabel, int sequenceNumber) implements DomainEvent {

    public static GradeCorrectedEvent correctedFor(Long gradeItemId, Long studentPersonId, String sectionName, String gradeTypeLabel,
                                                   int sequenceNumber) {
        return new GradeCorrectedEvent(CorrelationIds.current(), ApplicationTime.instant(), gradeItemId, studentPersonId, sectionName,
                gradeTypeLabel, sequenceNumber);
    }
}
