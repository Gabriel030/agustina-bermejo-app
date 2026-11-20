package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.util.UUID;

public record EmailChangeOwnerApprovalRequestedEvent(UUID eventId, String correlationId, Instant occurredAt, Long personId,
                                                     String currentEmail, String requestedEmail, String reviewLink)
        implements DeduplicableDomainEvent {

    public static EmailChangeOwnerApprovalRequestedEvent requestedFor(Long personId, String currentEmail, String requestedEmail,
                                                                      String reviewLink) {
        return new EmailChangeOwnerApprovalRequestedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(),
                personId, currentEmail, requestedEmail, reviewLink);
    }
}
