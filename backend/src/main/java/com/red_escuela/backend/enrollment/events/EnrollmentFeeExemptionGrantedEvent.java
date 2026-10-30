package com.red_escuela.backend.enrollment.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record EnrollmentFeeExemptionGrantedEvent(String correlationId, Instant occurredAt, Long programEnrollmentId, Long studentPersonId) implements DomainEvent {

    public static EnrollmentFeeExemptionGrantedEvent grantedFor(Long programEnrollmentId, Long studentPersonId) {
        return new EnrollmentFeeExemptionGrantedEvent(CorrelationIds.current(), ApplicationTime.instant(), programEnrollmentId, studentPersonId);
    }
}
