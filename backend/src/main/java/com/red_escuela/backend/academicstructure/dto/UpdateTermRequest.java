package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.shared.validation.RequestLimits;
import com.red_escuela.backend.shared.validation.SelfValidatingRequest;
import java.time.LocalDateTime;

public record UpdateTermRequest(
        String description, LocalDateTime startDate, LocalDateTime endDate,
        LocalDateTime enrollmentStart, LocalDateTime enrollmentEnd) implements SelfValidatingRequest {
    public UpdateTermRequest {
        RequestLimits.requireMaxLength("description", description, 255);
        RequestLimits.requireNotAfter("startDate", startDate, "endDate", endDate);
        RequestLimits.requireNotAfter("enrollmentStart", enrollmentStart, "enrollmentEnd", enrollmentEnd);
    }
}
