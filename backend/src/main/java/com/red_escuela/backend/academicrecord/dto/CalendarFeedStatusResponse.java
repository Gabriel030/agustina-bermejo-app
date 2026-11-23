package com.red_escuela.backend.academicrecord.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record CalendarFeedStatusResponse(
        boolean active,
        @Schema(nullable = true) LocalDateTime createdAt,
        @Schema(nullable = true) LocalDateTime lastUsedAt) {

    public static CalendarFeedStatusResponse inactive() {
        return new CalendarFeedStatusResponse(false, null, null);
    }
}
