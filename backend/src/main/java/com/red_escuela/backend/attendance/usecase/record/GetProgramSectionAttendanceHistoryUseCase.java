package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.dto.AttendanceHistoryResponse;

public interface GetProgramSectionAttendanceHistoryUseCase {
    AttendanceHistoryResponse get(GetProgramSectionAttendanceHistoryCommand command);
}
