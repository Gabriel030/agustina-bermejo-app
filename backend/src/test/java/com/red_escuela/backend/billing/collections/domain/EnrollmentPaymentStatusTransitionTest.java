package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.billing.collections.exception.IllegalEnrollmentPaymentStatusTransitionException;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.shared.domain.statemachine.ExhaustiveTransitionExpectations;
import com.red_escuela.backend.shared.domain.statemachine.StateTransitionRow;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.domain.statemachine.TransitionExpectation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.red_escuela.backend.billing.collections.domain.EnrollmentPaymentEvent.*;
import static com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-241")
@Tag("D-122")
@ExhaustiveTransitionExpectations(machine = EnrollmentPaymentStatusStateMachine.class)
class EnrollmentPaymentStatusTransitionTest {
    private static final StatusChangeActor PROCESS = StatusChangeActor.ofProcess("TEST");
    private static final LocalDate JUNE_FIRST = LocalDate.of(2027, 6, 1);

    private record Row(EnrollmentPaymentStatus from, EnrollmentPaymentEvent event, EnrollmentPaymentStatus to) {
    }

    private static final Set<Row> SPEC_TABLE = Set.of(
            new Row(null, STANDING_OPENED, UP_TO_DATE),
            new Row(UP_TO_DATE, OVERDUE_DEBT_DETECTED, OVERDUE),
            new Row(OVERDUE, OVERDUE_DEBT_CLEARED, UP_TO_DATE),
            new Row(OVERDUE, MONTH_CLOSED_WITH_UNPAID_INSTALLMENT, SUSPENDED),
            new Row(SUSPENDED, ACCOUNT_SETTLED, UP_TO_DATE),
            new Row(SUSPENDED, TERM_CLOSED_WHILE_SUSPENDED, WITHDRAWN_FOR_NONPAYMENT),
            new Row(SUSPENDED, COURSE_ENDED_WHILE_SUSPENDED, WITHDRAWN_FOR_NONPAYMENT));

    static List<TransitionExpectation> expectedTransitions() {
        List<EnrollmentPaymentStatus> states = new ArrayList<>();
        states.add(null);
        states.addAll(EnumSet.allOf(EnrollmentPaymentStatus.class));
        return states.stream().flatMap(from -> EnumSet.allOf(EnrollmentPaymentEvent.class).stream()
                .map(event -> expectedTarget(from, event)
                        .map(to -> TransitionExpectation.allows(from, event, to))
                        .orElseGet(() -> TransitionExpectation.rejects(from, event, IllegalEnrollmentPaymentStatusTransitionException.class)))).toList();
    }

    static Stream<Row> everyStatusAndEventPair() {
        return EnumSet.allOf(EnrollmentPaymentStatus.class).stream().flatMap(from -> EnumSet.allOf(EnrollmentPaymentEvent.class).stream()
                .map(event -> new Row(from, event, expectedTarget(from, event).orElse(null))));
    }

    private static Optional<EnrollmentPaymentStatus> expectedTarget(EnrollmentPaymentStatus from, EnrollmentPaymentEvent event) {
        return SPEC_TABLE.stream().filter(row -> row.from() == from && row.event() == event).map(Row::to).findFirst();
    }

    private static EnrollmentPaymentStanding standingIn(EnrollmentPaymentStatus status) {
        EnrollmentPaymentStanding standing = EnrollmentPaymentStanding.open(EnrollmentKind.COURSE, 7L, 9L, JUNE_FIRST.minusMonths(3), PROCESS);
        switch (status) {
            case UP_TO_DATE -> {
            }
            case OVERDUE -> standing.applyEvent(OVERDUE_DEBT_DETECTED, JUNE_FIRST.minusMonths(2), PROCESS);
            case SUSPENDED -> {
                standing.applyEvent(OVERDUE_DEBT_DETECTED, JUNE_FIRST.minusMonths(2), PROCESS);
                standing.applyEvent(MONTH_CLOSED_WITH_UNPAID_INSTALLMENT, JUNE_FIRST.minusMonths(1), PROCESS);
            }
            case WITHDRAWN_FOR_NONPAYMENT -> {
                standing.applyEvent(OVERDUE_DEBT_DETECTED, JUNE_FIRST.minusMonths(2), PROCESS);
                standing.applyEvent(MONTH_CLOSED_WITH_UNPAID_INSTALLMENT, JUNE_FIRST.minusMonths(1), PROCESS);
                standing.applyEvent(TERM_CLOSED_WHILE_SUSPENDED, JUNE_FIRST, PROCESS);
            }
        }
        return standing;
    }

    @Test
    void theTransitionTableIsExactlyTheOneInTheSpecification() {
        Set<Row> actual = EnrollmentPaymentStatusStateMachine.TRANSITIONS.listAllowedTransitions().stream()
                .map((StateTransitionRow<EnrollmentPaymentStatus, EnrollmentPaymentEvent> row) -> new Row(row.fromState(), row.event(), row.toState()))
                .collect(Collectors.toSet());

        assertThat(actual).isEqualTo(SPEC_TABLE);
    }

    @ParameterizedTest
    @MethodSource("everyStatusAndEventPair")
    void eachStatusAndEventPairEitherMovesAsTheTableSaysOrIsRejectedWithoutChangingAnything(Row pair) {
        EnrollmentPaymentStanding standing = standingIn(pair.from());
        int changesBefore = standing.statusChanges().size();
        YearMonth suspendedSinceBefore = standing.suspendedSince();

        if (pair.to() == null) {
            assertThatThrownBy(() -> standing.applyEvent(pair.event(), JUNE_FIRST, PROCESS)).isInstanceOf(IllegalEnrollmentPaymentStatusTransitionException.class);
            assertThat(standing.getStatus()).isEqualTo(pair.from());
            assertThat(standing.statusChanges()).hasSize(changesBefore);
            assertThat(standing.suspendedSince()).isEqualTo(suspendedSinceBefore);
            return;
        }
        standing.applyEvent(pair.event(), JUNE_FIRST, PROCESS);

        assertThat(standing.getStatus()).isEqualTo(pair.to());
        assertThat(standing.statusChanges()).hasSize(changesBefore + 1);
        EnrollmentPaymentStatusChange written = standing.statusChanges().get(changesBefore);
        assertThat(written.getFromStatus()).isEqualTo(pair.from().name());
        assertThat(written.getToStatus()).isEqualTo(pair.to().name());
        assertThat(written.getEvent()).isEqualTo(pair.event().name());
        assertThat(written.getReason()).isEqualTo(pair.event().reason());
        assertThat(written.getActorProcess()).isEqualTo("TEST");
        assertThat(written.getBusinessDate()).isEqualTo(JUNE_FIRST);
    }

    @Test
    void withdrawnForNonpaymentIsTerminal() {
        for (EnrollmentPaymentEvent event : EnrollmentPaymentEvent.values()) {
            assertThatThrownBy(() -> EnrollmentPaymentStatusStateMachine.resolveNextStatus(WITHDRAWN_FOR_NONPAYMENT, event))
                    .isInstanceOf(IllegalEnrollmentPaymentStatusTransitionException.class);
        }
    }

    @Test
    void theOnlyCreationEventIsStandingOpened() {
        for (EnrollmentPaymentEvent event : EnrollmentPaymentEvent.values()) {
            assertThat(EnrollmentPaymentStatusStateMachine.TRANSITIONS.findTargetStateFor(null, event)).isEqualTo(expectedTarget(null, event));
        }
    }

    @Test
    void aNewStandingStartsUpToDateWithItsInitialHistoryRow() {
        EnrollmentPaymentStanding standing = EnrollmentPaymentStanding.open(EnrollmentKind.PROGRAM, 3L, 4L, JUNE_FIRST, PROCESS);

        assertThat(standing.getStatus()).isEqualTo(UP_TO_DATE);
        assertThat(standing.kind()).isEqualTo(EnrollmentKind.PROGRAM);
        assertThat(standing.getProgramEnrollmentId()).isEqualTo(3L);
        assertThat(standing.getCourseEnrollmentId()).isNull();
        assertThat(standing.enrollmentId()).isEqualTo(3L);
        assertThat(standing.statusChanges()).singleElement().satisfies(change -> {
            assertThat(change.getFromStatus()).isNull();
            assertThat(change.getToStatus()).isEqualTo("UP_TO_DATE");
            assertThat(change.getEvent()).isEqualTo("STANDING_OPENED");
        });
    }

    @Test
    void aCourseStandingKeepsTheCourseEnrollmentId() {
        EnrollmentPaymentStanding standing = EnrollmentPaymentStanding.open(EnrollmentKind.COURSE, 5L, 4L, JUNE_FIRST, PROCESS);

        assertThat(standing.kind()).isEqualTo(EnrollmentKind.COURSE);
        assertThat(standing.getCourseEnrollmentId()).isEqualTo(5L);
        assertThat(standing.getProgramEnrollmentId()).isNull();
        assertThat(standing.enrollmentId()).isEqualTo(5L);
    }

    @Test
    void suspendedSinceIsTheMonthOfTheSuspensionAndIsClearedWhenItEnds() {
        EnrollmentPaymentStanding standing = standingIn(OVERDUE);

        standing.applyEvent(MONTH_CLOSED_WITH_UNPAID_INSTALLMENT, LocalDate.of(2027, 6, 1), PROCESS);
        assertThat(standing.suspendedSince()).isEqualTo(YearMonth.of(2027, 6));

        standing.applyEvent(ACCOUNT_SETTLED, LocalDate.of(2027, 6, 20), PROCESS);
        assertThat(standing.suspendedSince()).isNull();
    }
}
