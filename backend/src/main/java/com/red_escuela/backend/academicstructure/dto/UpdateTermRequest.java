package com.red_escuela.backend.academicstructure.dto;

import java.time.LocalDateTime;

public record UpdateTermRequest(
        String description, LocalDateTime startDate, LocalDateTime endDate,
        LocalDateTime enrollmentStart, LocalDateTime enrollmentEnd) {
}
