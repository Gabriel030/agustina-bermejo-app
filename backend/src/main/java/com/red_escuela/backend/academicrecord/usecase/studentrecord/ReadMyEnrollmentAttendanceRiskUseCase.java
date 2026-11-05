package com.red_escuela.backend.academicrecord.usecase.studentrecord;

import com.red_escuela.backend.academicrecord.dto.StudentAttendanceRiskResponse;

public interface ReadMyEnrollmentAttendanceRiskUseCase {
    StudentAttendanceRiskResponse read(Long studentId, Long enrollmentId);
}
