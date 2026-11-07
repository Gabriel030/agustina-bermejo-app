package com.red_escuela.backend.academicrecord.usecase;

import com.red_escuela.backend.academicrecord.dto.StudentAttendanceRiskResponse;

public interface ReadMyCourseEnrollmentAttendanceRiskUseCase {
    StudentAttendanceRiskResponse read(Long studentId, Long enrollmentId);
}
