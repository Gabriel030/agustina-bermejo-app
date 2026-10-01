package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.announcement.entity.AnnouncementCategory;
import com.red_escuela.backend.announcement.entity.AudienceSpec;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record UpdateAnnouncementCommand(
        Long announcementId,
        String title,
        String body,
        AnnouncementCategory category,
        boolean urgent,
        LocalDate expiresAt,
        LocalDateTime scheduledFor,
        AudienceSpec audienceSpec) {
}
