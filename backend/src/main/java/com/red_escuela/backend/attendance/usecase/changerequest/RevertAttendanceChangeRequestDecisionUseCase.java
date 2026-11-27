package com.red_escuela.backend.attendance.usecase.changerequest;

import com.red_escuela.backend.attendance.dto.AttendanceChangeRequestResponse;

public interface RevertAttendanceChangeRequestDecisionUseCase {
    AttendanceChangeRequestResponse revert(RevertAttendanceChangeRequestDecisionCommand command);
}
