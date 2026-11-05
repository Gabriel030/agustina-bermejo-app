package com.red_escuela.backend.attendance.events;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDate;

public record AttendanceJustificationRejectedEvent(String correlationId, Instant occurredAt, Long attendanceId, Long studentPersonId,
                                                   LocalDate classDate, String subjectName, String rejectionReason) implements DomainEvent {

    public static AttendanceJustificationRejectedEvent rejectedFor(Attendance attendance) {
        return new AttendanceJustificationRejectedEvent(CorrelationIds.current(), ApplicationTime.instant(), attendance.getId(),
                AttendanceJustificationEventSnapshot.studentPersonIdOf(attendance), attendance.getClassDate(),
                AttendanceJustificationEventSnapshot.subjectNameOf(attendance), attendance.getJustificationRejectionReason());
    }
}
