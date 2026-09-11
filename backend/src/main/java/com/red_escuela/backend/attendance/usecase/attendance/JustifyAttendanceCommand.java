package com.red_escuela.backend.attendance.usecase.attendance;

public record JustifyAttendanceCommand(Long attendanceId, String reason, String attachmentUrl) {
}
