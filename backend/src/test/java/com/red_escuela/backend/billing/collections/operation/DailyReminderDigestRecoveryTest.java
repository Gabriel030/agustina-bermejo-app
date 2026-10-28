package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.domain.DailyReminderDigest;
import com.red_escuela.backend.billing.collections.domain.FiredReminderOccurrence;
import com.red_escuela.backend.billing.collections.domain.MissedReminderRecovery;
import com.red_escuela.backend.billing.collections.domain.NonBusinessDays;
import com.red_escuela.backend.billing.collections.domain.ReminderDigestMode;
import com.red_escuela.backend.billing.collections.domain.ReminderInvoiceFact;
import com.red_escuela.backend.billing.collections.domain.ReminderMessage;
import com.red_escuela.backend.billing.collections.domain.ReminderStudentFacts;
import com.red_escuela.backend.billing.configuration.domain.ReminderChannel;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.billing.configuration.domain.ReminderMilestone;
import com.red_escuela.backend.billing.configuration.domain.ReminderNotice;
import com.red_escuela.backend.billing.configuration.domain.ReminderRepetition;
import com.red_escuela.backend.billing.configuration.domain.ResolvedReminderSchedule;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.red_escuela.backend.billing.collections.operation.ReminderDigestScenarios.bothChannels;
import static com.red_escuela.backend.billing.collections.operation.ReminderDigestScenarios.installment;
import static com.red_escuela.backend.billing.collections.operation.ReminderDigestScenarios.schedule;
import static org.assertj.core.api.Assertions.assertThat;

class DailyReminderDigestRecoveryTest {

    private static final long INVOICE_ID = 1L;
    private static final String DUE_DATE = "2027-03-11";

    private static LocalDate march(int day) {
        return LocalDate.of(2027, 3, day);
    }

    private static DailyReminderDigest plan(int day, ResolvedReminderSchedule schedule, MissedReminderRecovery recovery) {
        ReminderInvoiceFact fact = installment(INVOICE_ID, DUE_DATE);
        return DailyReminderDigestPlanner.planDailyDigestOf(new ReminderStudentFacts(7L, List.of(fact), Map.of()), Map.of(INVOICE_ID, schedule), march(day),
                NonBusinessDays.weekendsOnly(), ReminderDigestMode.DAILY_DIGEST, recovery);
    }

    private static MissedReminderRecovery missed(int... days) {
        return MissedReminderRecovery.ofMissedDays(java.util.Arrays.stream(days).mapToObj(DailyReminderDigestRecoveryTest::march).collect(java.util.stream.Collectors.toSet()));
    }

    private static List<String> noticeKeysOf(ReminderMessage message) {
        return message.lines().stream().flatMap(line -> line.notices().stream()).map(notice -> notice.noticeKey()).toList();
    }

    private static ResolvedReminderSchedule afterDue(String key, int offsetDays) {
        return schedule(bothChannels(key, ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, offsetDays));
    }

    @Test
    @Tag("D-436")
    void aMissedAfterDueNoticeIsRecoveredOnTheNextRun() {
        DailyReminderDigest recovered = plan(15, afterDue("after-due", 1), missed(12, 13, 14));
        DailyReminderDigest withoutRecovery = plan(15, afterDue("after-due", 1), MissedReminderRecovery.none());

        assertThat(recovered.emailMessages()).hasSize(1);
        assertThat(noticeKeysOf(recovered.emailMessages().get(0))).containsExactly("after-due");
        assertThat(recovered.inAppMessages()).hasSize(1);
        assertThat(withoutRecovery.hasNothingToSend()).isTrue();
    }

    @Test
    @Tag("D-436")
    void aNoticeOfADayThatWasNotMissedIsNotRecovered() {
        DailyReminderDigest digest = plan(15, afterDue("after-due", 1), missed(13, 14));

        assertThat(digest.hasNothingToSend()).isTrue();
    }

    @Test
    @Tag("D-436")
    void aMissedBeforeDueNoticeIsRecoveredOnlyWhileTheDueDateHasNotPassed() {
        ResolvedReminderSchedule beforeDue = schedule(bothChannels("before-due", ReminderMessageType.BEFORE_DUE_REMINDER, ReminderMilestone.DUE_DATE, -1));

        DailyReminderDigest onTheDueDate = plan(11, beforeDue, missed(10));
        DailyReminderDigest afterTheDueDate = plan(12, beforeDue, missed(10, 11));

        assertThat(noticeKeysOf(onTheDueDate.emailMessages().get(0))).containsExactly("before-due");
        assertThat(afterTheDueDate.hasNothingToSend()).isTrue();
    }

    @Test
    @Tag("D-436")
    void missedIssuanceAndAfterIssueNoticesAreRecoveredOnlyWhileNotDue() {
        ResolvedReminderSchedule afterIssue = schedule(bothChannels("after-issue", ReminderMessageType.AFTER_ISSUE_REMINDER, ReminderMilestone.ISSUANCE, 4));
        ResolvedReminderSchedule issuance = schedule(bothChannels("issuance", ReminderMessageType.ISSUANCE_NOTICE, ReminderMilestone.ISSUANCE, 4));

        assertThat(noticeKeysOf(plan(10, afterIssue, missed(5)).emailMessages().get(0))).containsExactly("after-issue");
        assertThat(plan(12, afterIssue, missed(5)).hasNothingToSend()).isTrue();
        assertThat(noticeKeysOf(plan(10, issuance, missed(5)).emailMessages().get(0))).containsExactly("issuance");
        assertThat(plan(12, issuance, missed(5)).hasNothingToSend()).isTrue();
    }

    @Test
    @Tag("D-436")
    void aMissedBeforeSuspensionNoticeIsRecoveredEvenAfterTheDueDate() {
        ResolvedReminderSchedule beforeSuspension = ReminderDigestScenarios.schedule(bothChannels("before-suspension", ReminderMessageType.BEFORE_SUSPENSION_REMINDER,
                ReminderMilestone.DUE_DATE, 1));

        DailyReminderDigest digest = plan(15, beforeSuspension, missed(12));

        assertThat(noticeKeysOf(digest.emailMessages().get(0))).containsExactly("before-suspension");
    }

    @Test
    @Tag("D-437")
    void severalMissedDaysAreGroupedIntoASingleMailAndASingleInAppMessage() {
        ResolvedReminderSchedule twoNotices = schedule(bothChannels("after-due-a", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 1),
                bothChannels("after-due-b", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 2));

        DailyReminderDigest digest = plan(15, twoNotices, missed(12, 13));

        assertThat(digest.emailMessages()).hasSize(1);
        assertThat(noticeKeysOf(digest.emailMessages().get(0))).containsExactlyInAnyOrder("after-due-a", "after-due-b");
        assertThat(digest.inAppMessages()).hasSize(1);
    }

    @Test
    @Tag("D-437")
    void anOccurrenceAlreadyHandledOnItsDayIsNotRecoveredAgainOnlyOnTheChannelHandled() {
        MissedReminderRecovery emailHandled = missed(12).withAlreadyHandled(Set.of(new FiredReminderOccurrence(INVOICE_ID, "after-due", ReminderChannel.EMAIL, march(12))));

        DailyReminderDigest digest = plan(15, afterDue("after-due", 1), emailHandled);

        assertThat(digest.emailMessages()).isEmpty();
        assertThat(digest.inAppMessages()).hasSize(1);
    }

    @Test
    @Tag("D-437")
    void aNoticeThatFiresTodayAndWasAlsoMissedIsListedOnce() {
        ResolvedReminderSchedule repeating = schedule(new ReminderNotice("after-due", ReminderMessageType.AFTER_DUE_REMINDER, ReminderMilestone.DUE_DATE, 1,
                EnumSet.allOf(ReminderChannel.class), new ReminderRepetition(3, ReminderMilestone.SUSPENSION)));

        DailyReminderDigest digest = plan(15, repeating, missed(12));

        assertThat(noticeKeysOf(digest.emailMessages().get(0))).containsExactly("after-due");
    }

    @Test
    @Tag("D-438")
    void aRecoveredMailCarriesTheDispatchPriorityOfItsMostSevereNotice() {
        DailyReminderDigest recovered = plan(15, afterDue("after-due", 1), missed(12));
        DailyReminderDigest onTime = plan(12, afterDue("after-due", 1), MissedReminderRecovery.none());

        assertThat(recovered.emailMessages().get(0).priority()).isEqualTo(onTime.emailMessages().get(0).priority());
        assertThat(recovered.emailMessages().get(0).leadMessageType()).isEqualTo(ReminderMessageType.AFTER_DUE_REMINDER);
    }
}
