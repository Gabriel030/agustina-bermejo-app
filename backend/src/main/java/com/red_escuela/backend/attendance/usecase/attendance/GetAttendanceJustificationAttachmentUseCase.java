package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.dto.AttendanceJustificationAttachment;

import java.util.Optional;

public interface GetAttendanceJustificationAttachmentUseCase {
    Optional<AttendanceJustificationAttachment> get(Long attendanceId);
}
