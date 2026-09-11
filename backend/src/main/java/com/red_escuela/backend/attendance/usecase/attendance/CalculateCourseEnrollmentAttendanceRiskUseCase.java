package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.dto.AttendanceRiskResponse;

public interface CalculateCourseEnrollmentAttendanceRiskUseCase {
    AttendanceRiskResponse calculate(Long enrollmentId);
}
