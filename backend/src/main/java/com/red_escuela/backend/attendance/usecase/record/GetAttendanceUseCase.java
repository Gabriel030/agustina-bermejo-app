package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.entity.Attendance;

public interface GetAttendanceUseCase {
    Attendance get(Long id);
}
