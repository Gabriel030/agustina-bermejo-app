package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.service.CourseService;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class DeactivateCourseService implements DeactivateCourseUseCase {

    private final CourseService courseService;

    public DeactivateCourseService(CourseService courseService) {
        this.courseService = courseService;
    }

    @Override
    public void execute(Long courseId) {
        courseService.deactivate(courseId);
    }
}
