package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.dto.CourseReadinessResponse;
import com.red_escuela.backend.academicstructure.service.CourseReadinessChecker;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetCourseReadinessService implements GetCourseReadinessUseCase {

    private final CourseReadinessChecker readinessChecker;

    public GetCourseReadinessService(CourseReadinessChecker readinessChecker) {
        this.readinessChecker = readinessChecker;
    }

    @Override
    public CourseReadinessResponse getCourseReadiness(Long courseId) {
        return readinessChecker.check(courseId);
    }
}
