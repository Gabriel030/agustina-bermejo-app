package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BankTransferApprovalRevokedEvent(UUID eventId, String correlationId, Instant occurredAt, Long paymentId, Long studentCurrentAccountId,
                                               LocalDate revokedOn, NonNegativeMoney amount) implements DeduplicableDomainEvent {

    public static BankTransferApprovalRevokedEvent revokedFor(Long paymentId, Long studentCurrentAccountId, LocalDate revokedOn, NonNegativeMoney amount) {
        return new BankTransferApprovalRevokedEvent(UUID.randomUUID(), CorrelationIds.current(), ApplicationTime.instant(), paymentId, studentCurrentAccountId,
                revokedOn, amount);
    }
}
