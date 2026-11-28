package com.red_escuela.backend.evaluation.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record ExamBoardAutoClosedEvent(String correlationId, Instant occurredAt, Long examBoardId, String subjectName,
                                       long absentCount) implements DomainEvent {

    public static ExamBoardAutoClosedEvent of(Long examBoardId, String subjectName, long absentCount) {
        return new ExamBoardAutoClosedEvent(CorrelationIds.current(), ApplicationTime.instant(), examBoardId, subjectName,
                absentCount);
    }
}
