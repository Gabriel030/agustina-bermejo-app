package com.red_escuela.backend.announcement.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;

public record AnnouncementReadEvent(String correlationId, Instant occurredAt, Long announcementId, Long readerPersonId) implements DomainEvent {

    public static AnnouncementReadEvent readFor(Long announcementId, Long readerPersonId) {
        return new AnnouncementReadEvent(CorrelationIds.current(), ApplicationTime.instant(), announcementId, readerPersonId);
    }
}
