package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.dto.CourseResponse;
import com.red_escuela.backend.academicstructure.entity.CatalogArea;
import com.red_escuela.backend.shared.domain.StatusType;
import java.util.List;
import com.red_escuela.backend.academicstructure.service.CourseService;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ListCoursesService implements ListCoursesUseCase {

    private final CourseService courseService;

    public ListCoursesService(CourseService courseService) {
        this.courseService = courseService;
    }

    @Override
    public List<CourseResponse> execute(StatusType status, Boolean visibleInCatalog, CatalogArea area) {
        return courseService.list(status, visibleInCatalog, area).stream().map(CourseResponse::from).toList();
    }
}
