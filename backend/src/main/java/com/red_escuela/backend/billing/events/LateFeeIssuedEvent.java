package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record LateFeeIssuedEvent(UUID eventId, String correlationId, Instant occurredAt, Long debitNoteId, String debitNoteNumber, Long originInvoiceId,
                                 String originInvoiceNumber, Long studentPersonId, int monthOrdinal, LocalDate factDate, LocalDate issueDate,
                                 Money amount) implements DeduplicableDomainEvent {

    public static final long ISSUING_WINDOW_DAYS = 15;

    public static LateFeeIssuedEvent issuedFor(Long debitNoteId, String debitNoteNumber, Long originInvoiceId, String originInvoiceNumber, Long studentPersonId,
                                               int monthOrdinal, LocalDate factDate, LocalDate issueDate, Money amount) {
        return new LateFeeIssuedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), debitNoteId, debitNoteNumber, originInvoiceId,
                originInvoiceNumber, studentPersonId, monthOrdinal, factDate, issueDate, amount);
    }

    public boolean issuedAfterFiscalWindow() {
        return ChronoUnit.DAYS.between(factDate, issueDate) > ISSUING_WINDOW_DAYS;
    }
}
