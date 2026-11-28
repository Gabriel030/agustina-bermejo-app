package com.red_escuela.backend.academicstructure.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CohortReadinessResponse(Long cohortId, boolean open, LocalDateTime openedAt, boolean ready, List<CohortReadinessItemResponse> items) {

    public List<String> missingBlockingCodes() {
        return items.stream().filter(item -> item.blocking() && !item.fulfilled()).map(item -> item.code().name()).toList();
    }
}
