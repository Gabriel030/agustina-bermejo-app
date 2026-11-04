package com.red_escuela.backend.announcement.dto;

import com.red_escuela.backend.announcement.entity.AnnouncementCategory;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record UpdateAnnouncementRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 1000) String body,
        @NotNull AnnouncementCategory category,
        boolean urgent,
        @Schema(nullable = true) LocalDate expiresAt,
        @Schema(nullable = true) @Future LocalDateTime scheduledFor,
        @Schema(nullable = true) AudienceSpecRequest audience,
        @JsonProperty("notify") Boolean notifyRecipients) {
}
