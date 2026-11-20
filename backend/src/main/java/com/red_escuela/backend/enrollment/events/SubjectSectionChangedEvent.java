package com.red_escuela.backend.enrollment.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.util.List;

public record SubjectSectionChangedEvent(String correlationId, Instant occurredAt, Long enrollmentId, Long studentPersonId,
        String studentName, String subjectName, String fromSectionName, String toSectionName, List<Long> fromInstructorPersonIds,
        List<Long> toInstructorPersonIds, boolean notifyStudent, String reason) implements DomainEvent {

    public static SubjectSectionChangedEvent of(Long enrollmentId, Long studentPersonId, String studentName, String subjectName,
            String fromSectionName, String toSectionName, List<Long> fromInstructorPersonIds, List<Long> toInstructorPersonIds,
            boolean notifyStudent, String reason) {
        return new SubjectSectionChangedEvent(CorrelationIds.current(), ApplicationTime.instant(), enrollmentId, studentPersonId,
                studentName, subjectName, fromSectionName, toSectionName, List.copyOf(fromInstructorPersonIds),
                List.copyOf(toInstructorPersonIds), notifyStudent, reason);
    }
}
