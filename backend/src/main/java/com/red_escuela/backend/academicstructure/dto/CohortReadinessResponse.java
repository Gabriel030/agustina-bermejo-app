package com.red_escuela.backend.academicstructure.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public record CohortReadinessResponse(Long cohortId, boolean open, @Schema(nullable = true) LocalDateTime openedAt, boolean ready,
                                      int commissionsTotal, int commissionsWithoutPreceptor, List<CohortReadinessItemResponse> items) {

    public List<String> missingBlockingCodes() {
        return items.stream().filter(item -> item.blocking() && !item.fulfilled()).map(item -> item.code().name()).toList();
    }
}
