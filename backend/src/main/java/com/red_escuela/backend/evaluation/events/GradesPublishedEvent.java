package com.red_escuela.backend.evaluation.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.util.List;

public record GradesPublishedEvent(String correlationId, Instant occurredAt, Long sectionId, String sectionName,
                                   String gradeTypeLabel, int sequenceNumber, List<Long> studentPersonIds) implements DomainEvent {

    public static GradesPublishedEvent publishedFor(Long sectionId, String sectionName, String gradeTypeLabel, int sequenceNumber,
                                                    List<Long> studentPersonIds) {
        return new GradesPublishedEvent(CorrelationIds.current(), ApplicationTime.instant(), sectionId, sectionName, gradeTypeLabel,
                sequenceNumber, studentPersonIds);
    }
}
