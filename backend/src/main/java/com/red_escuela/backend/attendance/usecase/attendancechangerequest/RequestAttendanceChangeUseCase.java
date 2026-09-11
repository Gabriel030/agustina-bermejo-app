package com.red_escuela.backend.attendance.usecase.attendancechangerequest;

import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;

public interface RequestAttendanceChangeUseCase {
    AttendanceChangeRequest request(RequestAttendanceChangeCommand command);
}
