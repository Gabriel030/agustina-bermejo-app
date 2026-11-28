package com.red_escuela.backend.evaluation.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record ExamBoardWithoutTribunalEvent(String correlationId, Instant occurredAt, Long examBoardId, String subjectName)
        implements DomainEvent {

    public static ExamBoardWithoutTribunalEvent of(Long examBoardId, String subjectName) {
        return new ExamBoardWithoutTribunalEvent(CorrelationIds.current(), ApplicationTime.instant(), examBoardId, subjectName);
    }
}
