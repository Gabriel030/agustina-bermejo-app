package com.red_escuela.backend.announcement.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.util.List;

public record AnnouncementPublishedEvent(String correlationId, Instant occurredAt, Long announcementId, String sectionName, List<Long> studentPersonIds) implements DomainEvent {

    public static AnnouncementPublishedEvent publishedFor(Long announcementId, String sectionName, List<Long> studentPersonIds) {
        return new AnnouncementPublishedEvent(CorrelationIds.current(), ApplicationTime.instant(), announcementId, sectionName, studentPersonIds);
    }
}
