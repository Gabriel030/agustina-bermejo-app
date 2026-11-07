package com.red_escuela.backend.attendance.usecase.changerequest;

import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;

import java.util.List;

public interface ListMyAttendanceChangeRequestsUseCase {
    List<AttendanceChangeRequest> list(Long instructorId);
}
