package com.red_escuela.backend.graduation.events;

import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record GraduationRequestRejectedEvent(String correlationId, Instant occurredAt, Long requestId, Long studentPersonId, String rejectionReason) implements DomainEvent {

    public static GraduationRequestRejectedEvent rejectedFor(GraduationRequest request) {
        return new GraduationRequestRejectedEvent(CorrelationIds.current(), ApplicationTime.instant(), request.getId(),
                request.getProgramEnrollment().getStudent().getPerson().getId(), request.getRejectionReason());
    }
}
