package com.red_escuela.backend.evaluation.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.util.List;

public record SubjectApprovalRevertedEvent(String correlationId, Instant occurredAt, Long studentPersonId, String subjectName,
                                           List<String> missingPrerequisiteNames) implements DomainEvent {

    public static SubjectApprovalRevertedEvent revertedFor(Long studentPersonId, String subjectName, List<String> missingPrerequisiteNames) {
        return new SubjectApprovalRevertedEvent(CorrelationIds.current(), ApplicationTime.instant(), studentPersonId, subjectName,
                missingPrerequisiteNames == null ? List.of() : List.copyOf(missingPrerequisiteNames));
    }
}
