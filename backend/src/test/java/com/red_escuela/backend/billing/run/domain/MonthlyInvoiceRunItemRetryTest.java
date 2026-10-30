package com.red_escuela.backend.billing.run.domain;

import com.red_escuela.backend.billing.run.exception.IllegalBillingRunTransitionException;
import com.red_escuela.backend.shared.domain.statemachine.ExhaustiveTransitionExpectations;
import com.red_escuela.backend.shared.domain.statemachine.TransitionExpectation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemEvent.*;
import static com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-160")
@ExhaustiveTransitionExpectations(machine = MonthlyInvoiceRunItemStateMachine.class)
class MonthlyInvoiceRunItemRetryTest {
    private static final LocalDateTime FIRST_DAY_AT_FOUR = LocalDateTime.of(2026, 10, 1, 4, 0);
    private static final FailedInvoiceRetrySchedule DEFAULT = new FailedInvoiceRetrySchedule(5, List.of(1, 3, 6, 12, 24));
    private static final MonthlyInvoiceItemOutcome FAILURE = MonthlyInvoiceItemOutcome.failed("Boom", "boom");

    private record Row(MonthlyInvoiceRunItemResult from, MonthlyInvoiceRunItemEvent event) {
    }

    private static final Map<Row, MonthlyInvoiceRunItemResult> SPEC_TABLE = Map.of(
            new Row(FAILED, RETRY_ISSUED), ISSUED,
            new Row(FAILED, RETRY_ALREADY_ISSUED), ALREADY_ISSUED,
            new Row(FAILED, RETRY_NOT_APPLICABLE), NOT_APPLICABLE,
            new Row(FAILED, RETRY_FAILED_WITH_WAIT), FAILED,
            new Row(FAILED, RETRY_FAILED_WITHOUT_WAIT), RETRIES_EXHAUSTED,
            new Row(RETRIES_EXHAUSTED, RETRY_ISSUED), ISSUED,
            new Row(RETRIES_EXHAUSTED, RETRY_ALREADY_ISSUED), ALREADY_ISSUED,
            new Row(RETRIES_EXHAUSTED, RETRY_NOT_APPLICABLE), NOT_APPLICABLE,
            new Row(RETRIES_EXHAUSTED, RETRY_FAILED_WITH_WAIT), FAILED,
            new Row(RETRIES_EXHAUSTED, RETRY_FAILED_WITHOUT_WAIT), RETRIES_EXHAUSTED);

    private static List<MonthlyInvoiceRunItemResult> statesIncludingNone() {
        List<MonthlyInvoiceRunItemResult> states = new ArrayList<>();
        states.add(null);
        states.addAll(EnumSet.allOf(MonthlyInvoiceRunItemResult.class));
        return states;
    }

    static List<TransitionExpectation> expectedTransitions() {
        return statesIncludingNone().stream().flatMap(from -> EnumSet.allOf(MonthlyInvoiceRunItemEvent.class).stream()
                .map(event -> SPEC_TABLE.containsKey(new Row(from, event))
                        ? TransitionExpectation.allows(from, event, SPEC_TABLE.get(new Row(from, event)))
                        : TransitionExpectation.rejects(from, event, IllegalBillingRunTransitionException.class))).toList();
    }

    @Test
    void everyResultAndEventPairMovesAsTheTableSaysOrIsRejected() {
        for (MonthlyInvoiceRunItemResult from : statesIncludingNone()) {
            for (MonthlyInvoiceRunItemEvent event : MonthlyInvoiceRunItemEvent.values()) {
                MonthlyInvoiceRunItemResult expected = SPEC_TABLE.get(new Row(from, event));
                if (expected == null) {
                    assertThatThrownBy(() -> MonthlyInvoiceRunItemStateMachine.resolveNextResult(from, event)).isInstanceOf(IllegalBillingRunTransitionException.class);
                } else {
                    assertThat(MonthlyInvoiceRunItemStateMachine.resolveNextResult(from, event)).isEqualTo(expected);
                }
            }
        }
    }

    private static MonthlyInvoiceRunItem failedOnFirstDay(FailedInvoiceRetrySchedule schedule) {
        return MonthlyInvoiceRunItem.recordItemOutcome(1L, MonthlyInvoiceEnrollmentType.PROGRAM, 7L, FAILURE, FIRST_DAY_AT_FOUR, schedule);
    }

    @Test
    void aFirstDayFailureWaitsOneHourAndIsNotDueBeforeIt() {
        MonthlyInvoiceRunItem item = failedOnFirstDay(DEFAULT);

        assertThat(item.getResult()).isEqualTo(FAILED);
        assertThat(item.getAttempts()).isEqualTo(1);
        assertThat(item.getNextRetryAt()).isEqualTo(FIRST_DAY_AT_FOUR.plusHours(1));
        assertThat(item.isDueForRetryAt(FIRST_DAY_AT_FOUR.plusMinutes(59))).isFalse();
        assertThat(item.isDueForRetryAt(FIRST_DAY_AT_FOUR.plusHours(1))).isTrue();
    }

    @Test
    void eachFailedRetryCountsAnAttemptAndSchedulesTheNextBackoff() {
        MonthlyInvoiceRunItem item = failedOnFirstDay(DEFAULT);
        LocalDateTime at = FIRST_DAY_AT_FOUR;
        int[] expectedWaits = {3, 6, 12, 24};
        for (int index = 0; index < expectedWaits.length; index++) {
            at = at.plusHours(2);
            item.recordRetryOutcome(FAILURE, DEFAULT, at);

            assertThat(item.getResult()).isEqualTo(FAILED);
            assertThat(item.getAttempts()).isEqualTo(index + 2);
            assertThat(item.getNextRetryAt()).isEqualTo(at.plusHours(expectedWaits[index]));
        }
    }

    @Test
    void theFailureAfterTheLastRetryExhaustsTheItemAndItIsNeverDueAgain() {
        MonthlyInvoiceRunItem item = failedOnFirstDay(DEFAULT);
        for (int retry = 1; retry <= 5; retry++) {
            item.recordRetryOutcome(FAILURE, DEFAULT, FIRST_DAY_AT_FOUR.plusDays(retry));
        }

        assertThat(item.getResult()).isEqualTo(RETRIES_EXHAUSTED);
        assertThat(item.getAttempts()).isEqualTo(6);
        assertThat(item.getNextRetryAt()).isNull();
        assertThat(item.isDueForRetryAt(FIRST_DAY_AT_FOUR.plusYears(1))).isFalse();
    }

    @Test
    void withZeroConfiguredRetriesTheFirstFailureIsExhaustedRightAway() {
        MonthlyInvoiceRunItem item = failedOnFirstDay(new FailedInvoiceRetrySchedule(0, List.of()));

        assertThat(item.getResult()).isEqualTo(RETRIES_EXHAUSTED);
        assertThat(item.getAttempts()).isEqualTo(1);
        assertThat(item.getNextRetryAt()).isNull();
    }

    @Test
    void aSuccessfulRetryClosesTheItemWithItsInvoicesAndClearsTheError() {
        MonthlyInvoiceRunItem item = failedOnFirstDay(DEFAULT);

        item.recordRetryOutcome(MonthlyInvoiceItemOutcome.issued(List.of(41L)), DEFAULT, FIRST_DAY_AT_FOUR.plusHours(2));

        assertThat(item.getResult()).isEqualTo(ISSUED);
        assertThat(item.getAttempts()).isEqualTo(2);
        assertThat(item.getInvoiceIds()).containsExactly(41L);
        assertThat(item.getErrorCode()).isNull();
        assertThat(item.getNextRetryAt()).isNull();
    }

    @Test
    void aRetryWhereThePlanNoLongerIssuesIsNotApplicableAndAnExistingInvoiceIsAlreadyIssued() {
        MonthlyInvoiceRunItem suspended = failedOnFirstDay(DEFAULT);
        MonthlyInvoiceRunItem duplicate = failedOnFirstDay(DEFAULT);

        suspended.recordRetryOutcome(MonthlyInvoiceItemOutcome.notApplicable(List.of("SUSPENDED_OR_WITHDRAWN")), DEFAULT, FIRST_DAY_AT_FOUR.plusHours(2));
        duplicate.recordRetryOutcome(MonthlyInvoiceItemOutcome.alreadyIssued(List.of(9L)), DEFAULT, FIRST_DAY_AT_FOUR.plusHours(2));

        assertThat(suspended.getResult()).isEqualTo(NOT_APPLICABLE);
        assertThat(suspended.getDecisionCode()).isEqualTo("SUSPENDED_OR_WITHDRAWN");
        assertThat(duplicate.getResult()).isEqualTo(ALREADY_ISSUED);
        assertThat(duplicate.getInvoiceIds()).containsExactly(9L);
    }

    @Test
    void onlyFailedAndExhaustedItemsCanBeRetriedByStaff() {
        MonthlyInvoiceRunItem failed = failedOnFirstDay(DEFAULT);
        MonthlyInvoiceRunItem exhausted = failedOnFirstDay(new FailedInvoiceRetrySchedule(0, List.of()));
        MonthlyInvoiceRunItem issued = MonthlyInvoiceRunItem.recordItemOutcome(1L, MonthlyInvoiceEnrollmentType.COURSE, 8L, MonthlyInvoiceItemOutcome.issued(List.of(1L)),
                FIRST_DAY_AT_FOUR, DEFAULT);
        MonthlyInvoiceRunItem notApplicable = MonthlyInvoiceRunItem.recordItemOutcome(1L, MonthlyInvoiceEnrollmentType.COURSE, 9L,
                MonthlyInvoiceItemOutcome.notApplicable(List.of("X")), FIRST_DAY_AT_FOUR, DEFAULT);
        MonthlyInvoiceRunItem alreadyIssued = MonthlyInvoiceRunItem.recordItemOutcome(1L, MonthlyInvoiceEnrollmentType.COURSE, 10L,
                MonthlyInvoiceItemOutcome.alreadyIssued(List.of(1L)), FIRST_DAY_AT_FOUR, DEFAULT);

        assertThat(failed.isRetryableByStaff()).isTrue();
        assertThat(exhausted.isRetryableByStaff()).isTrue();
        assertThat(issued.isRetryableByStaff()).isFalse();
        assertThat(notApplicable.isRetryableByStaff()).isFalse();
        assertThat(alreadyIssued.isRetryableByStaff()).isFalse();
    }

    @Test
    void anExhaustedItemRetriedByStaffClosesWithItsInvoiceAndNeverBecomesDueByItself() {
        MonthlyInvoiceRunItem exhausted = failedOnFirstDay(new FailedInvoiceRetrySchedule(0, List.of()));

        exhausted.recordRetryOutcome(FAILURE, new FailedInvoiceRetrySchedule(0, List.of()), FIRST_DAY_AT_FOUR.plusDays(3));

        assertThat(exhausted.getResult()).isEqualTo(RETRIES_EXHAUSTED);
        assertThat(exhausted.getAttempts()).isEqualTo(2);
        assertThat(exhausted.isDueForRetryAt(FIRST_DAY_AT_FOUR.plusYears(1))).isFalse();

        exhausted.recordRetryOutcome(MonthlyInvoiceItemOutcome.issued(List.of(3L)), DEFAULT, FIRST_DAY_AT_FOUR.plusDays(4));

        assertThat(exhausted.getResult()).isEqualTo(ISSUED);
        assertThat(exhausted.getAttempts()).isEqualTo(3);
        assertThat(exhausted.getInvoiceIds()).containsExactly(3L);
    }

    @Test
    void anItemThatIsNotFailedIsNeverDueAndRejectsAnotherRetry() {
        MonthlyInvoiceRunItem issued = MonthlyInvoiceRunItem.recordItemOutcome(1L, MonthlyInvoiceEnrollmentType.COURSE, 8L, MonthlyInvoiceItemOutcome.issued(List.of(1L)),
                FIRST_DAY_AT_FOUR, DEFAULT);

        assertThat(issued.isDueForRetryAt(FIRST_DAY_AT_FOUR.plusYears(1))).isFalse();
        assertThatThrownBy(() -> issued.recordRetryOutcome(FAILURE, DEFAULT, FIRST_DAY_AT_FOUR)).isInstanceOf(IllegalBillingRunTransitionException.class);
    }
}
