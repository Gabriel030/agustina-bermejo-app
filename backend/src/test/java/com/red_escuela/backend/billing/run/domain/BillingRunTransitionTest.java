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

import static com.red_escuela.backend.billing.run.domain.BillingRunEvent.*;
import static com.red_escuela.backend.billing.run.domain.BillingRunStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-447")
@ExhaustiveTransitionExpectations(machine = BillingRunStateMachine.class)
class BillingRunTransitionTest {

    private record Row(BillingRunStatus from, BillingRunEvent event) {
    }

    private static final LocalDateTime AT = LocalDateTime.of(2027, 3, 18, 3, 0);

    private static final Map<Row, BillingRunStatus> SPEC_TABLE = Map.of(
            new Row(null, STEP_STARTED), RUNNING,
            new Row(null, STEP_DECLARED_NOT_APPLICABLE), NOT_APPLICABLE,
            new Row(RUNNING, STEP_FINISHED_CLEAN), COMPLETED,
            new Row(RUNNING, STEP_FINISHED_WITH_ERRORS), COMPLETED_WITH_ERRORS,
            new Row(RUNNING, STEP_ABORTED), FAILED);

    private static List<BillingRunStatus> statesIncludingNone() {
        List<BillingRunStatus> states = new ArrayList<>();
        states.add(null);
        states.addAll(EnumSet.allOf(BillingRunStatus.class));
        return states;
    }

    static List<TransitionExpectation> expectedTransitions() {
        return statesIncludingNone().stream().flatMap(from -> EnumSet.allOf(BillingRunEvent.class).stream()
                .map(event -> SPEC_TABLE.containsKey(new Row(from, event))
                        ? TransitionExpectation.allows(from, event, SPEC_TABLE.get(new Row(from, event)))
                        : TransitionExpectation.rejects(from, event, IllegalBillingRunTransitionException.class))).toList();
    }

    @Test
    void everyStatusAndEventPairMovesAsTheTableSaysOrIsRejected() {
        for (BillingRunStatus from : statesIncludingNone()) {
            for (BillingRunEvent event : BillingRunEvent.values()) {
                BillingRunStatus expected = SPEC_TABLE.get(new Row(from, event));
                if (expected == null) {
                    assertThatThrownBy(() -> BillingRunStateMachine.resolveNextStatus(from, event)).isInstanceOf(IllegalBillingRunTransitionException.class);
                } else {
                    assertThat(BillingRunStateMachine.resolveNextStatus(from, event)).isEqualTo(expected);
                }
            }
        }
    }

    @Test
    void aStepWithFailedSubjectsFinishesWithErrorsAndACleanOneCompletes() {
        BillingRun withErrors = BillingRun.startStepRun(1L, BillingRunStep.OVERDUE_DETECTION, AT);
        withErrors.finishStepRun(new BillingRunStepOutcome(3, 2, 0, 1, List.of(BillingRunItemRecord.failedSubject(BillingRunSubjectType.INVOICE, 9L, "X", "boom"))), AT);
        BillingRun clean = BillingRun.startStepRun(1L, BillingRunStep.LATE_FEE_ACCRUAL, AT);
        clean.finishStepRun(new BillingRunStepOutcome(3, 3, 0, 0, List.of()), AT);

        assertThat(withErrors.getStatus()).isEqualTo(COMPLETED_WITH_ERRORS);
        assertThat(withErrors.getFailed()).isEqualTo(1);
        assertThat(clean.getStatus()).isEqualTo(COMPLETED);
        assertThat(clean.getProcessed()).isEqualTo(3);
    }

    @Test
    @Tag("D-447")
    void aNotApplicableStepKeepsItsCodeAndIsNotCountedAsFailed() {
        BillingRun gateway = BillingRun.declareStepNotApplicable(1L, BillingRunStep.GATEWAY_SYNC, "GATEWAY_DISABLED", AT);

        assertThat(gateway.getStatus()).isEqualTo(NOT_APPLICABLE);
        assertThat(gateway.getNotApplicableCode()).isEqualTo("GATEWAY_DISABLED");
        assertThat(gateway.getFailed()).isZero();
    }

    @Test
    void aFinishedStepCannotBeAbortedNorFinishedAgain() {
        BillingRun done = BillingRun.startStepRun(1L, BillingRunStep.LATE_FEE_ACCRUAL, AT);
        done.finishStepRun(new BillingRunStepOutcome(0, 0, 0, 0, List.of()), AT);

        assertThatThrownBy(() -> done.abortStepRun(AT)).isInstanceOf(IllegalBillingRunTransitionException.class);
        assertThatThrownBy(() -> done.finishStepRun(new BillingRunStepOutcome(0, 0, 0, 0, List.of()), AT)).isInstanceOf(IllegalBillingRunTransitionException.class);
    }
}
