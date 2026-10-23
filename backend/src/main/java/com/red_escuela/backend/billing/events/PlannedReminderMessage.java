package com.red_escuela.backend.billing.events;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public record PlannedReminderMessage(PlannedReminderFormat format, PlannedReminderNoticeType leadNoticeType, int priority, boolean precedesDueDate,
                                     List<PlannedReminderBlock> blocks, PlannedReminderSuspensionRule suspensionRule) {

    public PlannedReminderMessage {
        blocks = List.copyOf(blocks);
    }

    public List<PlannedReminderLine> lines() {
        return blocks.stream().flatMap(block -> block.lines().stream()).toList();
    }

    public Optional<PlannedReminderMessage> withoutLinesPrecedingTheirDueDate() {
        return withoutLinesMatching(PlannedReminderLine::precedesDueDate);
    }

    public Optional<PlannedReminderMessage> withoutLinesOfInvoices(Set<Long> invoiceIds) {
        return withoutLinesMatching(line -> invoiceIds.contains(line.invoiceId()));
    }

    private Optional<PlannedReminderMessage> withoutLinesMatching(Predicate<PlannedReminderLine> removed) {
        if (format == PlannedReminderFormat.DEBT_SUMMARY) {
            return Optional.of(this);
        }
        List<PlannedReminderBlock> remainingBlocks = blocks.stream().map(block -> block.withoutLinesMatching(removed)).flatMap(Optional::stream).toList();
        List<PlannedReminderLine> remainingLines = remainingBlocks.stream().flatMap(block -> block.lines().stream()).toList();
        List<PlannedReminderNoticeType> noticeTypes = remainingLines.stream().flatMap(line -> line.notices().stream()).map(PlannedReminderNotice::noticeType).toList();
        if (noticeTypes.isEmpty()) {
            return Optional.empty();
        }
        PlannedReminderNoticeType lead = noticeTypes.stream().max(Comparator.comparingInt(PlannedReminderNoticeType::ordinal)).orElseThrow();
        PlannedReminderFormat remainingFormat = noticeTypes.size() == 1 && remainingLines.size() == 1 ? PlannedReminderFormat.OWN_NOTICE : PlannedReminderFormat.DAILY_DIGEST;
        boolean remainingPrecedesDueDate = remainingLines.stream().filter(PlannedReminderLine::isHighlighted).allMatch(PlannedReminderLine::precedesDueDate);
        return Optional.of(new PlannedReminderMessage(remainingFormat, lead, lead.dispatchPriority(), remainingPrecedesDueDate, remainingBlocks, suspensionRule));
    }
}
