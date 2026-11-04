package com.red_escuela.backend.attendance.usecase.attendance;

public record JustifyAttendanceCommand(Long attendanceId, Long instructorIdIfDocente, String reason, String attachmentUrl) {
}
