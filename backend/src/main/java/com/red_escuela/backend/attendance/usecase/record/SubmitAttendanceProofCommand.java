package com.red_escuela.backend.attendance.usecase.record;

public record SubmitAttendanceProofCommand(Long attendanceId, String reason, String attachmentUrl) {
}
