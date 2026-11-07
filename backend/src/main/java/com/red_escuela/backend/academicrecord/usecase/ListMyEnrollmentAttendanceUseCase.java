package com.red_escuela.backend.academicrecord.usecase;

import com.red_escuela.backend.academicrecord.dto.StudentAttendanceResponse;

import java.util.List;

public interface ListMyEnrollmentAttendanceUseCase {
    List<StudentAttendanceResponse> list(Long studentId, Long enrollmentId);
}
