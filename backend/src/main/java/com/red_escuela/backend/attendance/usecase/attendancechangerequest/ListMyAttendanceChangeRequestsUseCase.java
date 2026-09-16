package com.red_escuela.backend.attendance.usecase.attendancechangerequest;

import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;

import java.util.List;

public interface ListMyAttendanceChangeRequestsUseCase {
    List<AttendanceChangeRequest> list(Long instructorId);
}
