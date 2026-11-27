package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.dto.CourseResponse;
import com.red_escuela.backend.academicstructure.dto.CreateCourseRequest;
import com.red_escuela.backend.academicstructure.service.CourseService;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CreateCourseService implements CreateCourseUseCase {

    private final CourseService courseService;

    public CreateCourseService(CourseService courseService) {
        this.courseService = courseService;
    }

    @Override
    public CourseResponse execute(CreateCourseRequest request) {
        return CourseResponse.from(courseService.create(request));
    }
}
