package com.red_escuela.backend.dto;

import java.time.LocalDateTime;

public record UpdateTermRequest(
        String description, LocalDateTime startDate, LocalDateTime endDate,
        LocalDateTime enrollmentStart, LocalDateTime enrollmentEnd) {
}
