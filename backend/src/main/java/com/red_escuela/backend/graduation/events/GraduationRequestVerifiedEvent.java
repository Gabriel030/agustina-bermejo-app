package com.red_escuela.backend.graduation.events;

import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record GraduationRequestVerifiedEvent(String correlationId, Instant occurredAt, Long requestId, Long studentPersonId, String programName) implements DomainEvent {

    public static GraduationRequestVerifiedEvent verifiedFor(GraduationRequest request) {
        return new GraduationRequestVerifiedEvent(CorrelationIds.current(), ApplicationTime.instant(), request.getId(),
                request.getProgramEnrollment().getStudent().getPerson().getId(), request.getProgramEnrollment().getProgram().getName());
    }
}
