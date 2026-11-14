package com.red_escuela.backend.enrollment.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.util.List;

public record SubjectEnrollmentWithdrawnEvent(String correlationId, Instant occurredAt, Long enrollmentId, Long studentPersonId,
        String studentName, String subjectName, String sectionName, List<Long> instructorPersonIds, boolean notifyStudent,
        String reason) implements DomainEvent {

    public static SubjectEnrollmentWithdrawnEvent of(Long enrollmentId, Long studentPersonId, String studentName, String subjectName,
            String sectionName, List<Long> instructorPersonIds, boolean notifyStudent, String reason) {
        return new SubjectEnrollmentWithdrawnEvent(CorrelationIds.current(), ApplicationTime.instant(), enrollmentId, studentPersonId,
                studentName, subjectName, sectionName, List.copyOf(instructorPersonIds), notifyStudent, reason);
    }
}
