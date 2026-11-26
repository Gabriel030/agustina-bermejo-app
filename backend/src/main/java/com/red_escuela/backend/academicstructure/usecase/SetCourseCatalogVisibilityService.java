package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.dto.CourseResponse;
import com.red_escuela.backend.academicstructure.exception.CourseNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.service.OfferingListPriceReader;
import com.red_escuela.backend.shared.UseCase;
import java.time.Clock;
import java.time.LocalDateTime;

@UseCase
public class SetCourseCatalogVisibilityService implements SetCourseCatalogVisibilityUseCase {

    private final CourseRepository courseRepository;

    private final OfferingListPriceReader listPriceReader;

    private final Clock clock;

    public SetCourseCatalogVisibilityService(CourseRepository courseRepository, OfferingListPriceReader listPriceReader, Clock clock) {
        this.courseRepository = courseRepository;
        this.listPriceReader = listPriceReader;
        this.clock = clock;
    }

    @Override
    public CourseResponse execute(SetCatalogVisibilityCommand command) {
        var courseId = command.offeringId();
        var course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        course.changeCatalogVisibility(command.visible(), command.area(), LocalDateTime.now(clock));
        var saved = courseRepository.save(course);
        return CourseResponse.from(saved, listPriceReader.findCourseTuition(courseId).orElse(null));
    }
}
