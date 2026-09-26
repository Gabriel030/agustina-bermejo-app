package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.announcement.entity.AnnouncementCategory;

import java.time.LocalDate;

public record UpdateAnnouncementCommand(
        Long announcementId,
        String title,
        String body,
        AnnouncementCategory category,
        boolean urgent,
        LocalDate expiresAt) {
}
