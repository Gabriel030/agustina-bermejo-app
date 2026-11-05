package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.events.PlannedReminderBlock;
import com.red_escuela.backend.billing.events.PlannedReminderFormat;
import com.red_escuela.backend.billing.events.PlannedReminderInvoiceKind;
import com.red_escuela.backend.billing.events.PlannedReminderLine;
import com.red_escuela.backend.billing.events.PlannedReminderMessage;
import com.red_escuela.backend.billing.events.PlannedReminderNotice;
import com.red_escuela.backend.billing.events.PlannedReminderNoticeType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectionReminderTest {

    private static final LocalDate MARCH_FIRST = LocalDate.of(2027, 3, 1);

    private static PlannedReminderMessage message(PlannedReminderFormat format, PlannedReminderNoticeType type, int priority, boolean precedesDueDate) {
        Money balance = Money.of(new BigDecimal("100000.00"), Currency.ARS);
        PlannedReminderLine line = new PlannedReminderLine(5L, null, PlannedReminderInvoiceKind.MONTHLY_INSTALLMENT, false, LocalDate.of(2027, 3, 11), balance, false,
                precedesDueDate, List.of(new PlannedReminderNotice("before-due", type)), 10L, 11L, 12L, 13L, null);
        PlannedReminderBlock block = new PlannedReminderBlock(Currency.ARS, List.of(line), balance, Money.of(new BigDecimal("30000.00"), Currency.ARS), 5L,
                Money.of(new BigDecimal("70000.00"), Currency.ARS));
        return new PlannedReminderMessage(format, type, priority, precedesDueDate, List.of(block), null);
    }

    @Test
    @Tag("D-353")
    void aReminderWithoutMessagesNorSuppressedNoticesCannotBeRecorded() {
        assertThatThrownBy(() -> CollectionReminder.recordPlannedFor(7L, MARCH_FIRST, List.of(), List.of(), List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Tag("D-353")
    void aReminderOnlyWithSuppressedNoticesCanBeRecorded() {
        SuppressedReminder suppressed = new SuppressedReminder(5L, "before-due", ReminderChannel.EMAIL, "RECEIPT_UNDER_REVIEW");

        CollectionReminder reminder = CollectionReminder.recordPlannedFor(7L, MARCH_FIRST, List.of(), List.of(), List.of(suppressed));

        assertThat(reminder.getSuppressed()).containsExactly(suppressed);
        assertThat(reminder.getInAppMessages()).isEmpty();
        assertThat(reminder.getEmailMessages()).isEmpty();
    }

    @Test
    @Tag("D-353")
    @Tag("D-410")
    void everyMessageKeepsItsFormatPriorityAndDueDateFlagAcrossTheStoredJson() {
        PlannedReminderMessage digest = message(PlannedReminderFormat.DAILY_DIGEST, PlannedReminderNoticeType.AFTER_DUE_REMINDER, 4, false);
        PlannedReminderMessage ownNotice = message(PlannedReminderFormat.OWN_NOTICE, PlannedReminderNoticeType.BEFORE_SUSPENSION_REMINDER, 5, true);

        CollectionReminder reminder = CollectionReminder.recordPlannedFor(7L, MARCH_FIRST, List.of(digest), List.of(digest, ownNotice), List.of());

        assertThat(reminder.getStudentId()).isEqualTo(7L);
        assertThat(reminder.getBusinessDate()).isEqualTo(MARCH_FIRST);
        assertThat(reminder.getInAppMessages()).containsExactly(digest);
        assertThat(reminder.getEmailMessages()).containsExactly(digest, ownNotice);
        assertThat(reminder.getEmailMessages()).extracting(PlannedReminderMessage::format, PlannedReminderMessage::priority, PlannedReminderMessage::precedesDueDate)
                .containsExactly(org.assertj.core.api.Assertions.tuple(PlannedReminderFormat.DAILY_DIGEST, 4, false),
                        org.assertj.core.api.Assertions.tuple(PlannedReminderFormat.OWN_NOTICE, 5, true));
        assertThat(reminder.getCreatedAt()).isNotNull();
    }

    @Test
    @Tag("D-353")
    void aReminderNeedsItsStudentAndItsBusinessDate() {
        PlannedReminderMessage digest = message(PlannedReminderFormat.DAILY_DIGEST, PlannedReminderNoticeType.AFTER_DUE_REMINDER, 4, false);

        assertThatThrownBy(() -> CollectionReminder.recordPlannedFor(null, MARCH_FIRST, List.of(digest), List.of(), List.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CollectionReminder.recordPlannedFor(7L, null, List.of(digest), List.of(), List.of())).isInstanceOf(NullPointerException.class);
    }
}
