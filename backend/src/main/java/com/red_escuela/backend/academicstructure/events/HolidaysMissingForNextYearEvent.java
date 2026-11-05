package com.red_escuela.backend.academicstructure.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

public record HolidaysMissingForNextYearEvent(UUID eventId, String correlationId, Instant occurredAt, int year, int weeksSinceWindowOpened) implements DeduplicableDomainEvent {

    public static HolidaysMissingForNextYearEvent missingFor(int year, int weeksSinceWindowOpened) {
        UUID oneIdentityPerWeek = UUID.nameUUIDFromBytes(("HolidaysMissingForNextYear:" + year + ":" + weeksSinceWindowOpened).getBytes(StandardCharsets.UTF_8));
        return new HolidaysMissingForNextYearEvent(oneIdentityPerWeek, CorrelationIds.current(), ApplicationTime.instant(), year, weeksSinceWindowOpened);
    }
}
