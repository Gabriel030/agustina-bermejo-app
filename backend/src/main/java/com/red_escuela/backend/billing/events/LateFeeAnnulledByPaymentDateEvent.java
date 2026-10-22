package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LateFeeAnnulledByPaymentDateEvent(UUID eventId, String correlationId, Instant occurredAt, Long creditNoteId, String creditNoteNumber, Long debitNoteId,
                                                String debitNoteNumber, Long studentPersonId, int annulledDays, LocalDate paymentDate, boolean paidWithinGrace,
                                                NonNegativeMoney totalAmount) implements DeduplicableDomainEvent {

    public static LateFeeAnnulledByPaymentDateEvent annulledFor(Long creditNoteId, String creditNoteNumber, Long debitNoteId, String debitNoteNumber, Long studentPersonId,
                                                                int annulledDays, LocalDate paymentDate, boolean paidWithinGrace, NonNegativeMoney totalAmount) {
        return new LateFeeAnnulledByPaymentDateEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), creditNoteId, creditNoteNumber, debitNoteId,
                debitNoteNumber, studentPersonId, annulledDays, paymentDate, paidWithinGrace, totalAmount);
    }
}
