package com.red_escuela.backend.enrollment.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDate;

public record ProgramEnrollmentGraduatedEvent(String correlationId, Instant occurredAt, Long studentId, Long programEnrollmentId, LocalDate effectiveDate) implements DomainEvent {

    public static ProgramEnrollmentGraduatedEvent graduatedOn(Long studentId, Long programEnrollmentId, LocalDate effectiveDate) {
        return new ProgramEnrollmentGraduatedEvent(CorrelationIds.current(), ApplicationTime.instant(), studentId, programEnrollmentId, effectiveDate);
    }
}
