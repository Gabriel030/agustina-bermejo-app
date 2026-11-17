package com.red_escuela.backend.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record WaitlistEntryResponse(int position, Long enrollmentId, WaitlistStudent student, LocalDateTime joinedAt,
                                    WaitlistVerdict verdict,
                                    @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDate expiresOn) {

    public record WaitlistStudent(Long id, String fullName, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String documentNumber) {
    }
}
