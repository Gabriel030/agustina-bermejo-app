package com.red_escuela.backend.academicrecord.usecase.studentrecord;

import com.red_escuela.backend.academicrecord.dto.StudentGradeItemResponse;

import java.util.List;

public interface ListMyCourseEnrollmentGradeItemsUseCase {
    List<StudentGradeItemResponse> list(Long studentId, Long enrollmentId);
}
