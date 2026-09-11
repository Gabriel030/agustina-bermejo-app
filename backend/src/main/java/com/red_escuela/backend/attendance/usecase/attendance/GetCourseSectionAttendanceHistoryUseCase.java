package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.dto.AttendanceHistoryResponse;

public interface GetCourseSectionAttendanceHistoryUseCase {
    AttendanceHistoryResponse get(GetCourseSectionAttendanceHistoryCommand command);
}
