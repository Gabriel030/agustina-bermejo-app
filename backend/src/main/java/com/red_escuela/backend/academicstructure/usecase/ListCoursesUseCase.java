package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.dto.CourseResponse;
import com.red_escuela.backend.shared.domain.StatusType;
import java.util.List;
public interface ListCoursesUseCase {
    List<CourseResponse> execute(StatusType status, Boolean visibleInCatalog);
}
