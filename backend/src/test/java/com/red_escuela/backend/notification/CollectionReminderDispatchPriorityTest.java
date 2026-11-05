package com.red_escuela.backend.notification;

import com.red_escuela.backend.billing.events.PlannedReminderNoticeType;
import com.red_escuela.backend.notification.delivery.domain.DispatchCandidate;
import com.red_escuela.backend.notification.delivery.domain.DispatchItem;
import com.red_escuela.backend.notification.delivery.domain.EmailDispatchPlan;
import com.red_escuela.backend.notification.delivery.domain.EmailDispatchPlanner;
import com.red_escuela.backend.notification.delivery.domain.PlannedDispatch;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionReminderDispatchPriorityTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);
    private static final LocalDate TOMORROW = LocalDate.of(2027, 3, 11);
    private static final List<PlannedReminderNoticeType> MOST_URGENT_FIRST = List.of(PlannedReminderNoticeType.BEFORE_SUSPENSION_REMINDER,
            PlannedReminderNoticeType.AFTER_DUE_REMINDER, PlannedReminderNoticeType.BEFORE_DUE_REMINDER, PlannedReminderNoticeType.AFTER_ISSUE_REMINDER,
            PlannedReminderNoticeType.ISSUANCE_NOTICE);

    @ParameterizedTest
    @EnumSource(PlannedReminderNoticeType.class)
    @Tag("D-343")
    void withACapOfOneTheMailOfTheMostUrgentTypeOfThoseWaitingGoesOut(PlannedReminderNoticeType expectedWinner) {
        int firstLessUrgent = MOST_URGENT_FIRST.indexOf(expectedWinner);
        List<PlannedReminderNoticeType> waiting = MOST_URGENT_FIRST.subList(firstLessUrgent, MOST_URGENT_FIRST.size()).reversed();
        List<DispatchCandidate> candidates = waiting.stream().map(type -> candidateOf(type)).toList();

        EmailDispatchPlan plan = new EmailDispatchPlanner().planFor(candidates, TODAY, TOMORROW, 1);

        assertThat(plan.toSend()).extracting(PlannedDispatch::deliveryId).containsExactly((long) expectedWinner.ordinal() + 1);
        assertThat(plan.toDefer()).hasSize(waiting.size() - 1);
    }

    @ParameterizedTest
    @EnumSource(PlannedReminderNoticeType.class)
    @Tag("D-343")
    void everyTypeHasADistinctPriorityFollowingTheAgreedOrder(PlannedReminderNoticeType type) {
        int expected = switch (type) {
            case BEFORE_SUSPENSION_REMINDER -> 1;
            case AFTER_DUE_REMINDER -> 2;
            case BEFORE_DUE_REMINDER -> 4;
            case AFTER_ISSUE_REMINDER -> 5;
            case ISSUANCE_NOTICE -> 6;
        };
        assertThat(type.dispatchPriority()).isEqualTo(expected);
    }

    private static DispatchCandidate candidateOf(PlannedReminderNoticeType type) {
        return new DispatchCandidate(type.ordinal() + 1, List.of(new DispatchItem("item-" + type, TOMORROW, false, type.dispatchPriority())));
    }

    @org.junit.jupiter.api.Test
    @Tag("D-343")
    @Tag("D-347")
    void aDebtSummaryGoesOutAfterAnAfterDueMailAndBeforeABeforeDueOne() {
        int debtSummary = com.red_escuela.backend.billing.collections.domain.ReminderMessageSeverity.DEBT_SUMMARY_DISPATCH_PRIORITY;

        assertThat(debtSummary).isGreaterThan(PlannedReminderNoticeType.AFTER_DUE_REMINDER.dispatchPriority()).isLessThan(PlannedReminderNoticeType.BEFORE_DUE_REMINDER.dispatchPriority());
    }
}
