package com.red_escuela.backend.announcement.usecase.announcement;

public record ListAnnouncementRecipientsCommand(Long announcementId, boolean instructorView) {
}
