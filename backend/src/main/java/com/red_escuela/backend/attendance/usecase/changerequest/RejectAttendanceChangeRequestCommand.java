package com.red_escuela.backend.attendance.usecase.changerequest;

public record RejectAttendanceChangeRequestCommand(Long requestId, String reason) {
}
