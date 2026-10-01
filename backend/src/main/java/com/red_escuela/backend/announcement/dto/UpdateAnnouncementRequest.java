package com.red_escuela.backend.announcement.dto;

import com.red_escuela.backend.announcement.entity.AnnouncementCategory;
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
        LocalDate expiresAt,
        @Future LocalDateTime scheduledFor,
        AudienceSpecRequest audience) {
}
