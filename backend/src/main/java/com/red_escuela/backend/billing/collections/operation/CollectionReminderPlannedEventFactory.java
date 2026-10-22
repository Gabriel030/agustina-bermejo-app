package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.domain.DailyReminderDigest;
import com.red_escuela.backend.billing.collections.domain.ReminderContentTerms;
import com.red_escuela.backend.billing.collections.domain.ReminderCurrencyBlock;
import com.red_escuela.backend.billing.collections.domain.ReminderDigestLine;
import com.red_escuela.backend.billing.collections.domain.ReminderDigestNotice;
import com.red_escuela.backend.billing.collections.domain.ReminderMessage;
import com.red_escuela.backend.billing.events.CollectionReminderPlannedEvent;
import com.red_escuela.backend.billing.events.PlannedReminderBlock;
import com.red_escuela.backend.billing.events.PlannedReminderFormat;
import com.red_escuela.backend.billing.events.PlannedReminderInvoiceKind;
import com.red_escuela.backend.billing.events.PlannedReminderLateFee;
import com.red_escuela.backend.billing.events.PlannedReminderLine;
import com.red_escuela.backend.billing.events.PlannedReminderMessage;
import com.red_escuela.backend.billing.events.PlannedReminderNotice;
import com.red_escuela.backend.billing.events.PlannedReminderNoticeType;
import com.red_escuela.backend.billing.events.PlannedReminderSuspensionRule;

import java.util.List;

public final class CollectionReminderPlannedEventFactory {

    private CollectionReminderPlannedEventFactory() {
    }

    public static CollectionReminderPlannedEvent plannedEventOf(Long reminderId, Long studentPersonId, DailyReminderDigest digest,
                                                                  ReminderContentTerms terms) {
        return CollectionReminderPlannedEvent.plannedFor(reminderId, studentPersonId, digest.businessDate(), inAppMessagesOf(digest, terms), emailMessagesOf(digest, terms));
    }

    public static List<PlannedReminderMessage> inAppMessagesOf(DailyReminderDigest digest, ReminderContentTerms terms) {
        return digest.inAppMessages().stream().map(message -> messageOf(message, terms)).toList();
    }

    public static List<PlannedReminderMessage> emailMessagesOf(DailyReminderDigest digest, ReminderContentTerms terms) {
        return digest.emailMessages().stream().map(message -> messageOf(message, terms)).toList();
    }

    private static PlannedReminderMessage messageOf(ReminderMessage message, ReminderContentTerms terms) {
        return new PlannedReminderMessage(PlannedReminderFormat.valueOf(message.format().name()), PlannedReminderNoticeType.valueOf(message.leadMessageType().name()),
                message.priority(), message.emailPrecedesDueDate(), message.blocks().stream().map(block -> blockOf(block, terms)).toList(),
                terms.findSuspensionRule().map(rule -> new PlannedReminderSuspensionRule(rule.afterDays(), rule.afterInvoiceCount())).orElse(null));
    }

    private static PlannedReminderBlock blockOf(ReminderCurrencyBlock block, ReminderContentTerms terms) {
        return new PlannedReminderBlock(block.currency(), block.lines().stream().map(line -> lineOf(line, terms)).toList(), block.total().value(),
                block.availableCredit().value(), block.shortfallInvoiceId(), block.missingForNext().value());
    }

    private static PlannedReminderLine lineOf(ReminderDigestLine line, ReminderContentTerms terms) {
        List<PlannedReminderNotice> notices = line.notices().stream().map(CollectionReminderPlannedEventFactory::noticeOf).toList();
        return new PlannedReminderLine(line.invoiceId(), line.originInvoiceId(), PlannedReminderInvoiceKind.valueOf(line.invoiceType().name()), line.debitNote(),
                line.dueDate(), line.balance().value(), line.receiptUnderReview(), line.precedesDueDate(), notices, line.versions().scheduleVersionId(),
                line.versions().exceptionVersionId(), line.versions().groupVersionId(), line.versions().globalSettingsVersionId(),
                terms.findLateFeeOf(line.invoiceId()).map(lateFee -> new PlannedReminderLateFee(lateFee.dailyRate(), lateFee.graceDays())).orElse(null));
    }

    private static PlannedReminderNotice noticeOf(ReminderDigestNotice notice) {
        return new PlannedReminderNotice(notice.noticeKey(), PlannedReminderNoticeType.valueOf(notice.messageType().name()));
    }
}
