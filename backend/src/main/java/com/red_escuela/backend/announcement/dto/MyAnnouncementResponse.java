package com.red_escuela.backend.announcement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.AnnouncementCategory;
import com.red_escuela.backend.announcement.specialist.AnnouncementSectionNameResolver;

import java.time.LocalDateTime;

public record MyAnnouncementResponse(
        Long id,
        String subjectName,
        String title,
        String body,
        AnnouncementCategory category,
        boolean urgent,
        String teacherName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        @Schema(nullable = true) LocalDateTime readAt) {

    public static MyAnnouncementResponse from(Announcement announcement, LocalDateTime readAt) {
        String subjectName = announcement.getSubjectSection() != null
                ? AnnouncementSectionNameResolver.of(announcement.getSubjectSection())
                : AnnouncementSectionNameResolver.of(announcement.getCourseSubjectSection());
        return new MyAnnouncementResponse(
                announcement.getId(),
                subjectName,
                announcement.getTitle(),
                announcement.getBody(),
                announcement.getCategory(),
                announcement.isUrgent(),
                announcement.getCreatedBy().getName() + " " + announcement.getCreatedBy().getLastname(),
                announcement.getCreatedAt(),
                announcement.getUpdatedAt(),
                readAt);
    }
}
