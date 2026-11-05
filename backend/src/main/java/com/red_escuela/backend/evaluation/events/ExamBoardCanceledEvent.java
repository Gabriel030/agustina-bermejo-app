package com.red_escuela.backend.evaluation.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record ExamBoardCanceledEvent(String correlationId, Instant occurredAt, Long examBoardId, String subjectName,
                                     LocalDateTime examDate, List<Long> registeredStudentPersonIds,
                                     List<Long> tribunalPersonIds, String reason) implements DomainEvent {

    public static ExamBoardCanceledEvent canceledFor(Long examBoardId, String subjectName, LocalDateTime examDate,
                                                     List<Long> registeredStudentPersonIds, List<Long> tribunalPersonIds,
                                                     String reason) {
        return new ExamBoardCanceledEvent(CorrelationIds.current(), ApplicationTime.instant(), examBoardId, subjectName,
                examDate, registeredStudentPersonIds, tribunalPersonIds, reason);
    }
}
