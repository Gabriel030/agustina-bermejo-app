package com.red_escuela.backend.billing.run.domain;

import com.red_escuela.backend.billing.run.exception.IllegalBillingRunTransitionException;
import com.red_escuela.backend.shared.domain.statemachine.ExhaustiveTransitionExpectations;
import com.red_escuela.backend.shared.domain.statemachine.TransitionExpectation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static com.red_escuela.backend.billing.run.domain.NightlyBillingRunEvent.*;
import static com.red_escuela.backend.billing.run.domain.NightlyBillingRunStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-356")
@ExhaustiveTransitionExpectations(machine = NightlyBillingRunStateMachine.class)
class NightlyBillingRunTransitionTest {

    private record Row(NightlyBillingRunStatus from, NightlyBillingRunEvent event) {
    }

    private static final Map<Row, NightlyBillingRunStatus> SPEC_TABLE = Map.of(
            new Row(null, NIGHT_STARTED), RUNNING,
            new Row(RUNNING, ALL_STEPS_FINISHED_CLEAN), COMPLETED,
            new Row(RUNNING, ALL_STEPS_FINISHED_WITH_ERRORS), COMPLETED_WITH_ERRORS,
            new Row(RUNNING, STEP_ABORTED), FAILED);

    private static List<NightlyBillingRunStatus> statesIncludingNone() {
        List<NightlyBillingRunStatus> states = new ArrayList<>();
        states.add(null);
        states.addAll(EnumSet.allOf(NightlyBillingRunStatus.class));
        return states;
    }

    static List<TransitionExpectation> expectedTransitions() {
        return statesIncludingNone().stream().flatMap(from -> EnumSet.allOf(NightlyBillingRunEvent.class).stream()
                .map(event -> SPEC_TABLE.containsKey(new Row(from, event))
                        ? TransitionExpectation.allows(from, event, SPEC_TABLE.get(new Row(from, event)))
                        : TransitionExpectation.rejects(from, event, IllegalBillingRunTransitionException.class))).toList();
    }

    @Test
    void everyStatusAndEventPairMovesAsTheTableSaysOrIsRejected() {
        for (NightlyBillingRunStatus from : statesIncludingNone()) {
            for (NightlyBillingRunEvent event : NightlyBillingRunEvent.values()) {
                NightlyBillingRunStatus expected = SPEC_TABLE.get(new Row(from, event));
                if (expected == null) {
                    assertThatThrownBy(() -> NightlyBillingRunStateMachine.resolveNextStatus(from, event)).isInstanceOf(IllegalBillingRunTransitionException.class);
                } else {
                    assertThat(NightlyBillingRunStateMachine.resolveNextStatus(from, event)).isEqualTo(expected);
                }
            }
        }
    }

    @Test
    void theClosingEventFollowsTheWorstStepStatus() {
        assertThat(NightlyBillingRunClosing.closingEventFor(List.of(BillingRunStatus.COMPLETED, BillingRunStatus.NOT_APPLICABLE))).isEqualTo(ALL_STEPS_FINISHED_CLEAN);
        assertThat(NightlyBillingRunClosing.closingEventFor(List.of(BillingRunStatus.COMPLETED, BillingRunStatus.COMPLETED_WITH_ERRORS))).isEqualTo(ALL_STEPS_FINISHED_WITH_ERRORS);
        assertThat(NightlyBillingRunClosing.closingEventFor(List.of(BillingRunStatus.COMPLETED_WITH_ERRORS, BillingRunStatus.FAILED))).isEqualTo(STEP_ABORTED);
        assertThat(NightlyBillingRunClosing.closingEventFor(List.of())).isEqualTo(ALL_STEPS_FINISHED_CLEAN);
    }
}
