package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DraftInvoicesAwaitingReviewEvent(UUID eventId, String correlationId, Instant occurredAt, Long monthlyInvoiceRunId, LocalDate period, LocalDate alertDate,
                                               long pendingDrafts) implements DeduplicableDomainEvent {

    public static DraftInvoicesAwaitingReviewEvent pendingOn(Long monthlyInvoiceRunId, LocalDate period, LocalDate alertDate, long pendingDrafts) {
        UUID oneIdentityPerRunAndDay = UUID.nameUUIDFromBytes(("DraftInvoicesAwaitingReview:" + monthlyInvoiceRunId + ":" + alertDate).getBytes(StandardCharsets.UTF_8));
        return new DraftInvoicesAwaitingReviewEvent(oneIdentityPerRunAndDay, CorrelationIds.current(), ApplicationTime.instant(), monthlyInvoiceRunId, period, alertDate,
                pendingDrafts);
    }
}
