package com.red_escuela.backend.academicrecord.usecase.studentrecord;

import com.red_escuela.backend.academicrecord.dto.StudentCourseSectionEnrollmentResponse;

public interface ReadMyCourseSectionEnrollmentUseCase {
    StudentCourseSectionEnrollmentResponse read(Long studentId, Long id);
}
