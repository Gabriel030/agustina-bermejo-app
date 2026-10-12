package com.red_escuela.backend.enrollment.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record CourseEnrollmentConfirmedByAdmissionPaymentEvent(String correlationId, Instant occurredAt, Long invoiceId, Long courseEnrollmentId) implements DomainEvent {

    public static CourseEnrollmentConfirmedByAdmissionPaymentEvent confirmedFor(Long invoiceId, Long courseEnrollmentId) {
        return new CourseEnrollmentConfirmedByAdmissionPaymentEvent(CorrelationIds.current(), ApplicationTime.instant(), invoiceId, courseEnrollmentId);
    }
}
