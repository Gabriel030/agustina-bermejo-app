package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.domain.CourseReadinessItemCode;

import java.util.List;

public record CourseReadinessItemResponse(CourseReadinessItemCode code, boolean blocking, boolean fulfilled, List<String> reasons) {
}
