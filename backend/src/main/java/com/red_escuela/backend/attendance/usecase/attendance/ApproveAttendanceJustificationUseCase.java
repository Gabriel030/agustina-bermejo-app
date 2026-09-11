package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.entity.Attendance;

public interface ApproveAttendanceJustificationUseCase {
    Attendance approve(Long attendanceId);
}
