package com.red_escuela.backend.attendance.usecase.record;

public record JustifyAttendanceCommand(Long attendanceId, String reason, String attachmentUrl, String exceptionReason) {
}
