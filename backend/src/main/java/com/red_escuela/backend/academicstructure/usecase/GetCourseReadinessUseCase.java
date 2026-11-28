package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.dto.CourseReadinessResponse;

public interface GetCourseReadinessUseCase {
    CourseReadinessResponse getCourseReadiness(Long courseId);
}
