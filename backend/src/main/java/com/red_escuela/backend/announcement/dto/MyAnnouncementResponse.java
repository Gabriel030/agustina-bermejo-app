package com.red_escuela.backend.announcement.dto;

import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.AnnouncementCategory;
import com.red_escuela.backend.announcement.service.AnnouncementSectionNames;

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
        LocalDateTime updatedAt) {

    public static MyAnnouncementResponse from(Announcement announcement) {
        String subjectName = announcement.getSubjectSection() != null
                ? AnnouncementSectionNames.of(announcement.getSubjectSection())
                : AnnouncementSectionNames.of(announcement.getCourseSubjectSection());
        return new MyAnnouncementResponse(
                announcement.getId(),
                subjectName,
                announcement.getTitle(),
                announcement.getBody(),
                announcement.getCategory(),
                announcement.isUrgent(),
                announcement.getCreatedBy().getName() + " " + announcement.getCreatedBy().getLastname(),
                announcement.getCreatedAt(),
                announcement.getUpdatedAt());
    }
}
