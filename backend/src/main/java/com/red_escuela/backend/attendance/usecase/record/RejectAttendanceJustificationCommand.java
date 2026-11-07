package com.red_escuela.backend.attendance.usecase.record;

public record RejectAttendanceJustificationCommand(Long attendanceId, String reason) {
}
