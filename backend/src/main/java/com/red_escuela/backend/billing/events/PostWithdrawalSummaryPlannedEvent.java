package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.shared.event.DeduplicableDomainEvent;
import com.red_escuela.backend.shared.time.ApplicationTime;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PostWithdrawalSummaryPlannedEvent(UUID eventId, String correlationId, Instant occurredAt, Long studentPersonId, LocalDate businessDate,
                                                List<Block> blocks) implements DeduplicableDomainEvent {

    public PostWithdrawalSummaryPlannedEvent {
        blocks = List.copyOf(blocks);
    }

    public static PostWithdrawalSummaryPlannedEvent plannedFor(Long studentPersonId, LocalDate businessDate, List<Block> blocks) {
        UUID eventIdOfTheStudentDay = UUID.nameUUIDFromBytes(("post-withdrawal-summary:" + studentPersonId + ":" + businessDate).getBytes(StandardCharsets.UTF_8));
        return new PostWithdrawalSummaryPlannedEvent(eventIdOfTheStudentDay, CorrelationIds.current(), ApplicationTime.instant(), studentPersonId, businessDate, blocks);
    }

    public record Block(List<Line> lines, Money total) {

        public Block {
            lines = List.copyOf(lines);
        }
    }

    public record Line(PlannedReminderInvoiceKind invoiceKind, LocalDate dueDate, Money balance) {
    }
}
