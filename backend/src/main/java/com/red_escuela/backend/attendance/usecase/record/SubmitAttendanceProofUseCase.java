package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.entity.Attendance;

public interface SubmitAttendanceProofUseCase {
    Attendance submit(SubmitAttendanceProofCommand command);
}
