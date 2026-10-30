package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.YearMonth;

public record SuspensionLiftedEvent(String correlationId, Instant occurredAt, EnrollmentType enrollmentType, Long enrollmentId, Long studentPersonId,
                                    YearMonth suspendedSince, YearMonth suspendedUntil) implements DomainEvent {

    public enum EnrollmentType {
        COURSE,
        PROGRAM
    }

    public static SuspensionLiftedEvent liftedForCourse(CourseEnrollment enrollment, YearMonth suspendedSince, YearMonth suspendedUntil) {
        return new SuspensionLiftedEvent(CorrelationIds.current(), ApplicationTime.instant(), EnrollmentType.COURSE, enrollment.getId(),
                enrollment.getStudent().getPerson().getId(), suspendedSince, suspendedUntil);
    }

    public static SuspensionLiftedEvent liftedForProgram(ProgramEnrollment enrollment, YearMonth suspendedSince, YearMonth suspendedUntil) {
        return new SuspensionLiftedEvent(CorrelationIds.current(), ApplicationTime.instant(), EnrollmentType.PROGRAM, enrollment.getId(),
                enrollment.getStudent().getPerson().getId(), suspendedSince, suspendedUntil);
    }
}
