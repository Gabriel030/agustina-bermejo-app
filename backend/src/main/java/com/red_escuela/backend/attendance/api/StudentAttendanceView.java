package com.red_escuela.backend.attendance.api;

import com.red_escuela.backend.attendance.entity.Attendance;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record StudentAttendanceView(
        Long id, Long enrollmentId, LocalDate classDate, String status,
        String justificationReason, String justificationAttachmentUrl, LocalDateTime justifiedAt) {

    static StudentAttendanceView from(Attendance attendance) {
        Long enrollmentId = attendance.getProgramSubjectSectionEnrollment() != null
                ? attendance.getProgramSubjectSectionEnrollment().getId()
                : attendance.getCourseSubjectSectionEnrollment().getId();
        return new StudentAttendanceView(
                attendance.getId(),
                enrollmentId,
                attendance.getClassDate(),
                attendance.getStatus().name(),
                attendance.getJustificationReason(),
                attendance.getJustificationAttachmentUrl(),
                attendance.getJustifiedAt());
    }
}
