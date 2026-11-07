package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.dto.AttendanceHistoryResponse;

public interface GetCourseSectionAttendanceHistoryUseCase {
    AttendanceHistoryResponse get(GetCourseSectionAttendanceHistoryCommand command);
}
