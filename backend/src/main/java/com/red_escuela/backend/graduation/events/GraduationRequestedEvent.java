package com.red_escuela.backend.graduation.events;

import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record GraduationRequestedEvent(String correlationId, Instant occurredAt, Long requestId, Long studentPersonId, String studentName, String programName) implements DomainEvent {

    public static GraduationRequestedEvent requestedFor(GraduationRequest request) {
        var person = request.getProgramEnrollment().getStudent().getPerson();
        return new GraduationRequestedEvent(CorrelationIds.current(), ApplicationTime.instant(), request.getId(), person.getId(),
                person.getName() + " " + person.getLastname(), request.getProgramEnrollment().getProgram().getName());
    }
}
