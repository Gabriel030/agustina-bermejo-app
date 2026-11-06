package com.red_escuela.backend.attendance.dto;

import com.red_escuela.backend.attendance.entity.Attendance;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AttendanceResponse(
        Long id, Long enrollmentId, LocalDate classDate, String status,
        String justificationReason, String justificationAttachmentUrl, LocalDateTime justifiedAt,
        String justificationRejectionReason,
        LocalDate justificationDeadline, Boolean justifiable) {

    public static AttendanceResponse from(Attendance attendance) {
        return from(attendance, null, null);
    }

    public static AttendanceResponse from(Attendance attendance, LocalDate justificationDeadline, Boolean justifiable) {
        Long enrollmentId = attendance.getProgramSubjectSectionEnrollment() != null
                ? attendance.getProgramSubjectSectionEnrollment().getId()
                : attendance.getCourseSubjectSectionEnrollment().getId();
        return new AttendanceResponse(
                attendance.getId(),
                enrollmentId,
                attendance.getClassDate(),
                attendance.getStatus().name(),
                attendance.getJustificationReason(),
                attendance.getJustificationAttachmentUrl(),
                attendance.getJustifiedAt(),
                attendance.getJustificationRejectionReason(),
                justificationDeadline,
                justifiable);
    }
}
