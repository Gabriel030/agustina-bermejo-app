package com.red_escuela.backend.academicrecord.usecase.studentrecord;

import com.red_escuela.backend.academicrecord.dto.StudentCourseSectionEnrollmentResponse;

import java.util.List;

public interface ListMyCourseSectionEnrollmentsUseCase {
    List<StudentCourseSectionEnrollmentResponse> list(Long studentId, Long courseEnrollmentId);
}
