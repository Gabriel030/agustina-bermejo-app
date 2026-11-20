package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.dto.CourseResponse;
import com.red_escuela.backend.academicstructure.exception.CourseNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.service.OfferingListPriceReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class SetCourseCatalogVisibilityService implements SetCourseCatalogVisibilityUseCase {

    private final CourseRepository courseRepository;

    private final OfferingListPriceReader listPriceReader;

    public SetCourseCatalogVisibilityService(CourseRepository courseRepository, OfferingListPriceReader listPriceReader) {
        this.courseRepository = courseRepository;
        this.listPriceReader = listPriceReader;
    }

    @Override
    public CourseResponse execute(Long courseId, boolean visible) {
        var course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        course.setVisibleInCatalog(visible);
        var saved = courseRepository.save(course);
        return CourseResponse.from(saved, listPriceReader.findCourseTuition(courseId).orElse(null));
    }
}
