package com.red_escuela.backend.identity.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.util.UUID;

public record GuardianConfirmationRequestedEvent(UUID eventId, String correlationId, Instant occurredAt, String guardianName,
                                                 String guardianEmail, String minorFirstName, String confirmationLink)
        implements DeduplicableDomainEvent {

    public static GuardianConfirmationRequestedEvent requestedFor(String guardianName, String guardianEmail,
                                                                  String minorFirstName, String confirmationLink) {
        return new GuardianConfirmationRequestedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(),
                guardianName, guardianEmail, minorFirstName, confirmationLink);
    }
}
