package com.red_escuela.backend.attendance.usecase.changerequest;

import com.red_escuela.backend.attendance.domain.AttendanceChangeRequestResult;

public record ListResolvedAttendanceChangeRequestsCommand(AttendanceChangeRequestResult result, int page, int size) {
}
