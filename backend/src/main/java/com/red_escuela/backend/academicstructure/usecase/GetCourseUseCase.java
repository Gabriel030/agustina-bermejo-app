package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.dto.CourseResponse;
public interface GetCourseUseCase {
    CourseResponse execute(Long courseId);
}
