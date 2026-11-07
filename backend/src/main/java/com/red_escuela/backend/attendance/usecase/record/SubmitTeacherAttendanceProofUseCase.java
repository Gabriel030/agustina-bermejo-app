package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.dto.AttendanceResponse;

public interface SubmitTeacherAttendanceProofUseCase {
    AttendanceResponse submit(SubmitTeacherAttendanceProofCommand command);
}
