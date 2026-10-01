package com.red_escuela.backend.announcement.dto;

import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.AnnouncementCategory;
import com.red_escuela.backend.announcement.entity.AnnouncementStatus;
import com.red_escuela.backend.announcement.entity.SectionType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AnnouncementResponse(
        Long id,
        SectionType sectionType,
        Long sectionId,
        String title,
        String body,
        AnnouncementCategory category,
        boolean urgent,
        LocalDate expiresAt,
        String audienceLabel,
        AnnouncementStatus status,
        LocalDateTime scheduledFor,
        LocalDateTime sentAt,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static AnnouncementResponse from(Announcement announcement) {
        return new AnnouncementResponse(
                announcement.getId(),
                announcement.getSectionType(),
                announcement.getSectionId(),
                announcement.getTitle(),
                announcement.getBody(),
                announcement.getCategory(),
                announcement.isUrgent(),
                announcement.getExpiresAt(),
                announcement.getAudienceLabel(),
                announcement.getStatus(),
                announcement.getScheduledFor(),
                announcement.getSentAt(),
                announcement.getCreatedBy().getName() + " " + announcement.getCreatedBy().getLastname(),
                announcement.getCreatedAt(),
                announcement.getUpdatedAt());
    }
}
