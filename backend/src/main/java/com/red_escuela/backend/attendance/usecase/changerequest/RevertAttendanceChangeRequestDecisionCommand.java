package com.red_escuela.backend.attendance.usecase.changerequest;

public record RevertAttendanceChangeRequestDecisionCommand(Long requestId, String reason) {
}
