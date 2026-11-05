package com.red_escuela.backend.attendance.events;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDate;

public record AttendanceJustificationApprovedEvent(String correlationId, Instant occurredAt, Long attendanceId, Long studentPersonId,
                                                   LocalDate classDate, String subjectName) implements DomainEvent {

    public static AttendanceJustificationApprovedEvent approvedFor(Attendance attendance) {
        return new AttendanceJustificationApprovedEvent(CorrelationIds.current(), ApplicationTime.instant(), attendance.getId(),
                AttendanceJustificationEventSnapshot.studentPersonIdOf(attendance), attendance.getClassDate(),
                AttendanceJustificationEventSnapshot.subjectNameOf(attendance));
    }
}
