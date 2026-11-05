package com.red_escuela.backend.attendance.usecase.attendancechangerequest;

import com.red_escuela.backend.attendance.dto.CreateAttendanceChangeRequestRequest;

public record RequestAttendanceChangeCommand(Long attendanceId, Long requestedById, CreateAttendanceChangeRequestRequest body) {
}
