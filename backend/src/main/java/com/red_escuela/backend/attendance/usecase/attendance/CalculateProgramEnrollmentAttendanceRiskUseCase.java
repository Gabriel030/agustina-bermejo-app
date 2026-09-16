package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.dto.AttendanceRiskResponse;

public interface CalculateProgramEnrollmentAttendanceRiskUseCase {
    AttendanceRiskResponse calculate(Long enrollmentId);
}
