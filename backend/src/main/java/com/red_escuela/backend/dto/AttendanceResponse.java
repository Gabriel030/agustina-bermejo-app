package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.Attendance;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AttendanceResponse(
        Long id, Long enrollmentId, LocalDate classDate, String status,
        String justificationReason, String justificationAttachmentUrl, LocalDateTime justifiedAt) {

    public static AttendanceResponse from(Attendance attendance) {
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
                attendance.getJustifiedAt());
    }
}
