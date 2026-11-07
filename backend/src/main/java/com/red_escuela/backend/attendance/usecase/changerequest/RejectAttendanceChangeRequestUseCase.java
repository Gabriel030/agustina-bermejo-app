package com.red_escuela.backend.attendance.usecase.changerequest;

import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;

public interface RejectAttendanceChangeRequestUseCase {
    AttendanceChangeRequest reject(RejectAttendanceChangeRequestCommand command);
}
