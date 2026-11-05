package com.red_escuela.backend.attendance.usecase.attendance;

public record RejectAttendanceJustificationCommand(Long attendanceId, String reason) {
}
