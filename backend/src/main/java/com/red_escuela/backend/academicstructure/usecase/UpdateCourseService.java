package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.dto.CourseResponse;
import com.red_escuela.backend.academicstructure.dto.UpdateCourseRequest;
import com.red_escuela.backend.academicstructure.service.CourseService;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdateCourseService implements UpdateCourseUseCase {

    private final CourseService courseService;

    public UpdateCourseService(CourseService courseService) {
        this.courseService = courseService;
    }

    @Override
    public CourseResponse execute(Long courseId, UpdateCourseRequest request) {
        return CourseResponse.from(courseService.update(courseId, request));
    }
}
