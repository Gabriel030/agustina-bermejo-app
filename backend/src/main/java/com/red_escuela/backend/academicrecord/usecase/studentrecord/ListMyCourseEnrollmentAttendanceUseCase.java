package com.red_escuela.backend.academicrecord.usecase.studentrecord;

import com.red_escuela.backend.academicrecord.dto.StudentAttendanceResponse;

import java.util.List;

public interface ListMyCourseEnrollmentAttendanceUseCase {
    List<StudentAttendanceResponse> list(Long studentId, Long enrollmentId);
}
