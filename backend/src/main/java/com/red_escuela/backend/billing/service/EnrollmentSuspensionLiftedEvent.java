package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.YearMonth;

public record EnrollmentSuspensionLiftedEvent(String correlationId, Instant occurredAt, EnrollmentType enrollmentType, Long enrollmentId,
                                              YearMonth suspendedSince, YearMonth suspendedUntil) implements DomainEvent {

    public enum EnrollmentType {
        COURSE,
        PROGRAM
    }

    public static EnrollmentSuspensionLiftedEvent liftedFor(EnrollmentType enrollmentType, Long enrollmentId, YearMonth suspendedSince,
                                                            YearMonth suspendedUntil) {
        return new EnrollmentSuspensionLiftedEvent(CorrelationIds.current(), ApplicationTime.instant(), enrollmentType, enrollmentId, suspendedSince, suspendedUntil);
    }

    public boolean concernsProgramEnrollment() {
        return enrollmentType == EnrollmentType.PROGRAM;
    }
}
