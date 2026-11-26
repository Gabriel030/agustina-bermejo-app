package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.dto.CourseResponse;
import com.red_escuela.backend.academicstructure.dto.UpdateCourseRequest;
public interface UpdateCourseUseCase {
    CourseResponse execute(Long courseId, UpdateCourseRequest request);
}
