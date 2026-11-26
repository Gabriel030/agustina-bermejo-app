package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.dto.CourseResponse;
import com.red_escuela.backend.academicstructure.dto.CreateCourseRequest;
public interface CreateCourseUseCase {
    CourseResponse execute(CreateCourseRequest request);
}
