package com.red_escuela.backend.announcement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.announcement.entity.AnnouncementCategory;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CreateAnnouncementRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 1000) String body,
        @NotNull AnnouncementCategory category,
        boolean urgent,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDate expiresAt,
        @Future @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDateTime scheduledFor,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) AudienceSpecRequest audience) {
}
