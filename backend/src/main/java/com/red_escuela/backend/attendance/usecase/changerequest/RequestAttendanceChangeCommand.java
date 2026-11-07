package com.red_escuela.backend.attendance.usecase.changerequest;

import com.red_escuela.backend.attendance.dto.CreateAttendanceChangeRequestRequest;

public record RequestAttendanceChangeCommand(Long attendanceId, Long requestedById, CreateAttendanceChangeRequestRequest body) {
}
