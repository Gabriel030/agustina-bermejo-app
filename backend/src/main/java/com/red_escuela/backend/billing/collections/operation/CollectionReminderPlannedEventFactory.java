package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.domain.DailyReminderDigest;
import com.red_escuela.backend.billing.collections.domain.ReminderCurrencyBlock;
import com.red_escuela.backend.billing.collections.domain.ReminderDigestLine;
import com.red_escuela.backend.billing.collections.domain.ReminderDigestNotice;
import com.red_escuela.backend.billing.collections.domain.ReminderMessage;
import com.red_escuela.backend.billing.events.CollectionReminderPlannedEvent;
import com.red_escuela.backend.billing.events.PlannedReminderBlock;
import com.red_escuela.backend.billing.events.PlannedReminderFormat;
import com.red_escuela.backend.billing.events.PlannedReminderInvoiceKind;
import com.red_escuela.backend.billing.events.PlannedReminderLine;
import com.red_escuela.backend.billing.events.PlannedReminderMessage;
import com.red_escuela.backend.billing.events.PlannedReminderNotice;
import com.red_escuela.backend.billing.events.PlannedReminderNoticeType;

import java.util.List;

public final class CollectionReminderPlannedEventFactory {

    private CollectionReminderPlannedEventFactory() {
    }

    public static CollectionReminderPlannedEvent plannedEventOf(Long reminderId, Long studentPersonId, DailyReminderDigest digest) {
        return CollectionReminderPlannedEvent.plannedFor(reminderId, studentPersonId, digest.businessDate(),
                digest.inAppMessages().stream().map(CollectionReminderPlannedEventFactory::messageOf).toList(),
                digest.emailMessages().stream().map(CollectionReminderPlannedEventFactory::messageOf).toList());
    }

    private static PlannedReminderMessage messageOf(ReminderMessage message) {
        return new PlannedReminderMessage(PlannedReminderFormat.valueOf(message.format().name()), PlannedReminderNoticeType.valueOf(message.leadMessageType().name()),
                message.priority(), message.emailPrecedesDueDate(), message.blocks().stream().map(CollectionReminderPlannedEventFactory::blockOf).toList());
    }

    private static PlannedReminderBlock blockOf(ReminderCurrencyBlock block) {
        return new PlannedReminderBlock(block.currency(), block.lines().stream().map(CollectionReminderPlannedEventFactory::lineOf).toList(), block.total().value(),
                block.availableCredit().value(), block.shortfallInvoiceId(), block.missingForNext().value());
    }

    private static PlannedReminderLine lineOf(ReminderDigestLine line) {
        List<PlannedReminderNotice> notices = line.notices().stream().map(CollectionReminderPlannedEventFactory::noticeOf).toList();
        return new PlannedReminderLine(line.invoiceId(), line.originInvoiceId(), PlannedReminderInvoiceKind.valueOf(line.invoiceType().name()), line.debitNote(),
                line.dueDate(), line.balance().value(), line.receiptUnderReview(), line.precedesDueDate(), notices, line.versions().scheduleVersionId(),
                line.versions().exceptionVersionId(), line.versions().groupVersionId(), line.versions().globalSettingsVersionId());
    }

    private static PlannedReminderNotice noticeOf(ReminderDigestNotice notice) {
        return new PlannedReminderNotice(notice.noticeKey(), PlannedReminderNoticeType.valueOf(notice.messageType().name()));
    }
}
