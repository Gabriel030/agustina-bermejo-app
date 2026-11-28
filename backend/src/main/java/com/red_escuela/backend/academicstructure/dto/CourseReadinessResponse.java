package com.red_escuela.backend.academicstructure.dto;

import java.util.List;

public record CourseReadinessResponse(Long courseId, boolean ready, boolean complete, List<CourseReadinessItemResponse> items) {

    public List<String> missingBlockingCodes() {
        return items.stream().filter(item -> item.blocking() && !item.fulfilled()).map(item -> item.code().name()).toList();
    }
}
