package com.red_escuela.backend.enrollment.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.util.UUID;

public record AdmissionEnrollmentStalledEvent(UUID eventId, String correlationId, Instant occurredAt, Long applicationId,
                                              String studentFullName, String programName) implements DeduplicableDomainEvent {

    public static AdmissionEnrollmentStalledEvent stalledFor(Long applicationId, String studentFullName, String programName) {
        return new AdmissionEnrollmentStalledEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(),
                applicationId, studentFullName, programName);
    }
}
