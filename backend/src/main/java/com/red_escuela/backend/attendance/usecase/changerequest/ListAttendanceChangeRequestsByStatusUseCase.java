package com.red_escuela.backend.attendance.usecase.changerequest;

import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;

import java.util.List;

public interface ListAttendanceChangeRequestsByStatusUseCase {
    List<AttendanceChangeRequest> list(AttendanceChangeRequestStatus status);
}
