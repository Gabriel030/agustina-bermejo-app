package com.red_escuela.backend.academicrecord.dto;

import com.red_escuela.backend.attendance.api.StudentAttendanceView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(name = "AttendanceResponse")
public record StudentAttendanceResponse(
        Long id, Long enrollmentId, LocalDate classDate, String status,
        String justificationReason, String justificationAttachmentUrl, LocalDateTime justifiedAt) {

    public static StudentAttendanceResponse from(StudentAttendanceView view) {
        return new StudentAttendanceResponse(
                view.id(), view.enrollmentId(), view.classDate(), view.status(),
                view.justificationReason(), view.justificationAttachmentUrl(), view.justifiedAt());
    }
}
