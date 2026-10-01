package com.red_escuela.backend.announcement.usecase.announcement;

import com.red_escuela.backend.announcement.entity.AnnouncementCategory;
import com.red_escuela.backend.announcement.entity.AudienceSpec;
import com.red_escuela.backend.announcement.entity.SectionType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CreateAnnouncementCommand(
        SectionType sectionType,
        Long sectionId,
        String title,
        String body,
        AnnouncementCategory category,
        boolean urgent,
        LocalDate expiresAt,
        LocalDateTime scheduledFor,
        AudienceSpec audienceSpec) {
}
