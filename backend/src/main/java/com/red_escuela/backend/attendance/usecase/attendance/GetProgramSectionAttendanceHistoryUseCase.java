package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.dto.AttendanceHistoryResponse;

public interface GetProgramSectionAttendanceHistoryUseCase {
    AttendanceHistoryResponse get(GetProgramSectionAttendanceHistoryCommand command);
}
