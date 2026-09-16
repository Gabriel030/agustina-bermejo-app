package com.red_escuela.backend.attendance.usecase.attendance;

public record SubmitAttendanceProofCommand(Long attendanceId, String reason, String attachmentUrl) {
}
