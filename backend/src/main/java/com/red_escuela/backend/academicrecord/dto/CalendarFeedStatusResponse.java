package com.red_escuela.backend.academicrecord.dto;

import java.time.LocalDateTime;

public record CalendarFeedStatusResponse(boolean active, LocalDateTime createdAt, LocalDateTime lastUsedAt) {

    public static CalendarFeedStatusResponse inactive() {
        return new CalendarFeedStatusResponse(false, null, null);
    }
}
