package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.dto.AttendanceResponse;

import java.util.List;

public interface ListProgramEnrollmentAttendanceDetailUseCase {
    List<AttendanceResponse> list(Long enrollmentId);
}
