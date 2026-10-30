package com.red_escuela.backend.announcement.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record AnnouncementDeletedEvent(String correlationId, Instant occurredAt, Long announcementId) implements DomainEvent {

    public static AnnouncementDeletedEvent deletedFor(Long announcementId) {
        return new AnnouncementDeletedEvent(CorrelationIds.current(), ApplicationTime.instant(), announcementId);
    }
}
