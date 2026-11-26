package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.dto.CourseResponse;

public interface SetCourseCatalogVisibilityUseCase {
    CourseResponse execute(SetCatalogVisibilityCommand command);
}
