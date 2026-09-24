package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.dto.CourseResponse;
import com.red_escuela.backend.academicstructure.exception.CourseNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class SetCourseCatalogVisibilityService implements SetCourseCatalogVisibilityUseCase {

    private final CourseRepository courseRepository;

    public SetCourseCatalogVisibilityService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Override
    public CourseResponse execute(Long courseId, boolean visible) {
        var course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        course.setVisibleInCatalog(visible);
        return CourseResponse.from(courseRepository.save(course));
    }
}
