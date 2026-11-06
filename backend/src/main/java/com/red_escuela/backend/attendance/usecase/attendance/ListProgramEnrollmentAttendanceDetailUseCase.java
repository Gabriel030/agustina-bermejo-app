package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.dto.AttendanceResponse;

import java.util.List;

public interface ListProgramEnrollmentAttendanceDetailUseCase {
    List<AttendanceResponse> list(Long enrollmentId);
}
