package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.dto.AttendanceRiskResponse;

public interface CalculateCourseEnrollmentAttendanceRiskUseCase {
    AttendanceRiskResponse calculate(Long enrollmentId);
}
