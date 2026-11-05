package com.red_escuela.backend.evaluation.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record ExamBoardRescheduledEvent(String correlationId, Instant occurredAt, Long examBoardId, String subjectName,
                                        LocalDateTime oldExamDate, LocalDateTime newExamDate, String classroom,
                                        List<Long> registeredStudentPersonIds, List<Long> tribunalPersonIds) implements DomainEvent {

    public static ExamBoardRescheduledEvent rescheduledFor(Long examBoardId, String subjectName, LocalDateTime oldExamDate,
                                                           LocalDateTime newExamDate, String classroom,
                                                           List<Long> registeredStudentPersonIds, List<Long> tribunalPersonIds) {
        return new ExamBoardRescheduledEvent(CorrelationIds.current(), ApplicationTime.instant(), examBoardId, subjectName,
                oldExamDate, newExamDate, classroom, registeredStudentPersonIds, tribunalPersonIds);
    }
}
