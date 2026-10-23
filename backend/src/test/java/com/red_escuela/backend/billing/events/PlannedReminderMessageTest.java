package com.red_escuela.backend.billing.events;

import com.red_escuela.backend.billing.collections.domain.ReminderMessageSeverity;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.util.Optional;

import static com.red_escuela.backend.billing.events.PlannedReminderFixtures.arsBlock;
import static com.red_escuela.backend.billing.events.PlannedReminderFixtures.block;
import static com.red_escuela.backend.billing.events.PlannedReminderFixtures.digest;
import static com.red_escuela.backend.billing.events.PlannedReminderFixtures.line;
import static com.red_escuela.backend.billing.events.PlannedReminderFixtures.ownNotice;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;

class PlannedReminderMessageTest {
    private static final LocalDate OVERDUE_DUE_DATE = LocalDate.of(2027, 3, 5);
    private static final LocalDate UPCOMING_DUE_DATE = LocalDate.of(2027, 3, 20);

    @Test
    @Tag("D-412")
    void filteringKeepsOnlyTheLinesThatFollowTheirDueDateAndRecomputesTheTotal() {
        PlannedReminderLine upcoming = line(1, UPCOMING_DUE_DATE, ars(70000), true, PlannedReminderNoticeType.BEFORE_DUE_REMINDER);
        PlannedReminderLine overdue = line(2, OVERDUE_DUE_DATE, ars(100000), false, PlannedReminderNoticeType.AFTER_DUE_REMINDER);
        PlannedReminderMessage message = digest(PlannedReminderNoticeType.AFTER_DUE_REMINDER, false, arsBlock(overdue, upcoming));

        PlannedReminderMessage filtered = message.withoutLinesPrecedingTheirDueDate().orElseThrow();

        assertThat(filtered.lines()).extracting(PlannedReminderLine::invoiceId).containsExactly(2L);
        assertThat(filtered.blocks().get(0).total()).isEqualTo(ars(100000));
        assertThat(filtered.format()).isEqualTo(PlannedReminderFormat.OWN_NOTICE);
        assertThat(filtered.precedesDueDate()).isFalse();
    }

    @Test
    @Tag("D-412")
    void aMessageWhoseNoticesAllPrecedeTheirDueDateLeavesNothingToSend() {
        PlannedReminderLine first = line(1, UPCOMING_DUE_DATE, ars(70000), true, PlannedReminderNoticeType.BEFORE_DUE_REMINDER);
        PlannedReminderLine second = line(2, UPCOMING_DUE_DATE, ars(30000), true, PlannedReminderNoticeType.AFTER_ISSUE_REMINDER);
        PlannedReminderMessage message = digest(PlannedReminderNoticeType.BEFORE_DUE_REMINDER, true, arsBlock(first, second));

        assertThat(message.withoutLinesPrecedingTheirDueDate()).isEmpty();
    }

    @Test
    @Tag("D-412")
    void aDebtLineWithoutNoticeThatFollowsItsDueDateDoesNotKeepAnEmptiedMessageAlive() {
        PlannedReminderLine upcoming = line(1, UPCOMING_DUE_DATE, ars(70000), true, PlannedReminderNoticeType.BEFORE_DUE_REMINDER);
        PlannedReminderLine overdueWithoutNotice = line(2, OVERDUE_DUE_DATE, ars(100000), false);
        PlannedReminderMessage message = digest(PlannedReminderNoticeType.BEFORE_DUE_REMINDER, true, arsBlock(overdueWithoutNotice, upcoming));

        assertThat(message.withoutLinesPrecedingTheirDueDate()).isEmpty();
    }

    @Test
    @Tag("D-412")
    @Tag("D-372")
    void theLeadNoticeAndThePriorityAreRecomputedFromTheRemainingNotices() {
        PlannedReminderLine previousButSevere = line(1, UPCOMING_DUE_DATE, ars(70000), true, PlannedReminderNoticeType.AFTER_DUE_REMINDER);
        PlannedReminderLine remainingMild = line(2, OVERDUE_DUE_DATE, ars(100000), false, PlannedReminderNoticeType.AFTER_ISSUE_REMINDER);
        PlannedReminderMessage message = digest(PlannedReminderNoticeType.AFTER_DUE_REMINDER, false, arsBlock(previousButSevere, remainingMild));

        PlannedReminderMessage filtered = message.withoutLinesPrecedingTheirDueDate().orElseThrow();

        assertThat(filtered.leadNoticeType()).isEqualTo(PlannedReminderNoticeType.AFTER_ISSUE_REMINDER);
        assertThat(filtered.priority()).isEqualTo(5);
    }

    @Test
    @Tag("D-412")
    void severalRemainingNoticesKeepTheDigestFormat() {
        PlannedReminderLine previous = line(1, UPCOMING_DUE_DATE, ars(70000), true, PlannedReminderNoticeType.BEFORE_DUE_REMINDER);
        PlannedReminderLine firstOverdue = line(2, OVERDUE_DUE_DATE, ars(100000), false, PlannedReminderNoticeType.AFTER_DUE_REMINDER);
        PlannedReminderLine secondOverdue = line(3, OVERDUE_DUE_DATE, ars(50000), false, PlannedReminderNoticeType.AFTER_DUE_REMINDER);
        PlannedReminderMessage message = digest(PlannedReminderNoticeType.AFTER_DUE_REMINDER, false, arsBlock(firstOverdue, secondOverdue, previous));

        PlannedReminderMessage filtered = message.withoutLinesPrecedingTheirDueDate().orElseThrow();

        assertThat(filtered.format()).isEqualTo(PlannedReminderFormat.DAILY_DIGEST);
        assertThat(filtered.blocks().get(0).total()).isEqualTo(ars(150000));
    }

    @Test
    @Tag("D-412")
    @Tag("D-097")
    void aCurrencyBlockWithoutRemainingLinesDisappearsAndTheOtherKeepsItsOwnTotal() {
        PlannedReminderLine dollarsUpcoming = line(1, UPCOMING_DUE_DATE, Money.of(new java.math.BigDecimal("100.00"), Currency.USD), true,
                PlannedReminderNoticeType.BEFORE_DUE_REMINDER);
        PlannedReminderLine pesosOverdue = line(2, OVERDUE_DUE_DATE, ars(100000), false, PlannedReminderNoticeType.AFTER_DUE_REMINDER);
        PlannedReminderBlock dollars = block(Currency.USD, Money.of(new java.math.BigDecimal("100.00"), Currency.USD), Money.zero(Currency.USD), null,
                Money.zero(Currency.USD), dollarsUpcoming);
        PlannedReminderMessage message = digest(PlannedReminderNoticeType.AFTER_DUE_REMINDER, false, arsBlock(pesosOverdue), dollars);

        PlannedReminderMessage filtered = message.withoutLinesPrecedingTheirDueDate().orElseThrow();

        assertThat(filtered.blocks()).extracting(PlannedReminderBlock::currency).containsExactly(Currency.ARS);
    }

    @Test
    @Tag("D-412")
    @Tag("D-139")
    void theCreditShortfallIsRecomputedOverTheRemainingDebtWhenItsInvoiceWasFiltered() {
        PlannedReminderLine previous = line(1, UPCOMING_DUE_DATE, ars(70000), true, PlannedReminderNoticeType.BEFORE_DUE_REMINDER);
        PlannedReminderLine overdue = line(2, OVERDUE_DUE_DATE, ars(100000), false, PlannedReminderNoticeType.AFTER_DUE_REMINDER);
        PlannedReminderBlock withShortfallOnThePrevious = block(Currency.ARS, ars(170000), ars(30000), 1L, ars(40000), previous, overdue);
        PlannedReminderMessage message = digest(PlannedReminderNoticeType.AFTER_DUE_REMINDER, false, withShortfallOnThePrevious);

        PlannedReminderBlock filtered = message.withoutLinesPrecedingTheirDueDate().orElseThrow().blocks().get(0);

        assertThat(filtered.shortfallInvoiceId()).isEqualTo(2L);
        assertThat(filtered.missingForNext()).isEqualTo(ars(70000));
        assertThat(filtered.availableCredit()).isEqualTo(ars(30000));
    }

    @Test
    @Tag("D-412")
    @Tag("D-139")
    void theCreditShortfallOfAnInvoiceNotListedInTheMessageIsKept() {
        PlannedReminderLine exemptOverdue = line(2, OVERDUE_DUE_DATE, ars(100000), false, PlannedReminderNoticeType.BEFORE_SUSPENSION_REMINDER);
        PlannedReminderBlock block = block(Currency.ARS, ars(100000), ars(30000), 1L, ars(40000), exemptOverdue);
        PlannedReminderMessage message = ownNotice(PlannedReminderNoticeType.BEFORE_SUSPENSION_REMINDER, false, block);

        PlannedReminderBlock filtered = message.withoutLinesPrecedingTheirDueDate().orElseThrow().blocks().get(0);

        assertThat(filtered.shortfallInvoiceId()).isEqualTo(1L);
        assertThat(filtered.missingForNext()).isEqualTo(ars(40000));
    }

    @Test
    @Tag("D-412")
    void aShortfallFullyCoveredByTheCreditLeavesNoMissingAmountAfterFiltering() {
        PlannedReminderLine previous = line(1, UPCOMING_DUE_DATE, ars(70000), true, PlannedReminderNoticeType.BEFORE_DUE_REMINDER);
        PlannedReminderLine overdue = line(2, OVERDUE_DUE_DATE, ars(20000), false, PlannedReminderNoticeType.AFTER_DUE_REMINDER);
        PlannedReminderBlock block = block(Currency.ARS, ars(90000), ars(30000), 1L, ars(40000), previous, overdue);
        PlannedReminderMessage message = digest(PlannedReminderNoticeType.AFTER_DUE_REMINDER, false, block);

        PlannedReminderBlock filtered = message.withoutLinesPrecedingTheirDueDate().orElseThrow().blocks().get(0);

        assertThat(filtered.shortfallInvoiceId()).isNull();
        assertThat(filtered.hasCreditShortfall()).isFalse();
        assertThat(filtered.missingForNext()).isEqualTo(Money.zero(Currency.ARS));
    }

    @ParameterizedTest
    @EnumSource(PlannedReminderNoticeType.class)
    @Tag("D-412")
    void theNoticeTypeOrderMatchesTheSeverityRankOfTheDomain(PlannedReminderNoticeType noticeType) {
        assertThat(noticeType.ordinal() + 1).isEqualTo(ReminderMessageSeverity.rankOf(ReminderMessageType.valueOf(noticeType.name())));
        assertThat(noticeType.dispatchPriority()).isEqualTo(ReminderMessageSeverity.dispatchPriorityOf(ReminderMessageType.valueOf(noticeType.name())));
    }

    @Test
    @Tag("D-412")
    void aMessageWithoutOptedOutLinesIsReturnedWithTheSameContent() {
        PlannedReminderLine overdue = line(2, OVERDUE_DUE_DATE, ars(100000), false, PlannedReminderNoticeType.AFTER_DUE_REMINDER);
        PlannedReminderMessage message = ownNotice(PlannedReminderNoticeType.AFTER_DUE_REMINDER, false, arsBlock(overdue));

        Optional<PlannedReminderMessage> filtered = message.withoutLinesPrecedingTheirDueDate();

        assertThat(filtered).contains(message);
    }
}
