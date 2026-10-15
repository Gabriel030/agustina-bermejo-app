package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LateFeeCorrectionIssuedAfterFiscalWindowEvent(UUID eventId, String correlationId, Instant occurredAt, Long creditNoteId, String creditNoteNumber,
                                                            Long debitNoteId, String debitNoteNumber, LocalDate withdrawalRegisteredOn, LocalDate issueDate)
        implements DeduplicableDomainEvent {

    public static LateFeeCorrectionIssuedAfterFiscalWindowEvent issuedFor(Long creditNoteId, String creditNoteNumber, Long debitNoteId, String debitNoteNumber,
                                                                         LocalDate withdrawalRegisteredOn, LocalDate issueDate) {
        return new LateFeeCorrectionIssuedAfterFiscalWindowEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), creditNoteId,
                creditNoteNumber, debitNoteId, debitNoteNumber, withdrawalRegisteredOn, issueDate);
    }
}
