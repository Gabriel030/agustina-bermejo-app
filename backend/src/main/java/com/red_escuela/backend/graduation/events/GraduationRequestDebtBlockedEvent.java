package com.red_escuela.backend.graduation.events;

import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record GraduationRequestDebtBlockedEvent(String correlationId, Instant occurredAt, Long requestId, Long studentPersonId, String programName,
                                                Money totalDue, int installmentCount) implements DomainEvent {

    public static GraduationRequestDebtBlockedEvent blockedFor(GraduationRequest request, Money totalDue, int installmentCount) {
        return new GraduationRequestDebtBlockedEvent(CorrelationIds.current(), ApplicationTime.instant(), request.getId(),
                request.getProgramEnrollment().getStudent().getPerson().getId(), request.getProgramEnrollment().getProgram().getName(),
                totalDue, installmentCount);
    }
}
