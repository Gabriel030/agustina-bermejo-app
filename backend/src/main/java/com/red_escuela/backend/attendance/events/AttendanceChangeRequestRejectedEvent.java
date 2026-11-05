package com.red_escuela.backend.attendance.events;

import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDate;

public record AttendanceChangeRequestRejectedEvent(String correlationId, Instant occurredAt, Long requestId, Long requestedByPersonId,
                                              String studentFullName, LocalDate classDate, String previousStatusLabel, String requestedStatusLabel) implements DomainEvent {

    public static AttendanceChangeRequestRejectedEvent rejectedFor(AttendanceChangeRequest request) {
        return new AttendanceChangeRequestRejectedEvent(CorrelationIds.current(), ApplicationTime.instant(), request.getId(),
                request.getRequestedBy().getPerson().getId(), AttendanceChangeRequestEventSnapshot.studentFullNameOf(request),
                request.getAttendance().getClassDate(), AttendanceChangeRequestEventSnapshot.labelOf(request.getPreviousStatus()),
                AttendanceChangeRequestEventSnapshot.labelOf(request.getRequestedStatus()));
    }
}
