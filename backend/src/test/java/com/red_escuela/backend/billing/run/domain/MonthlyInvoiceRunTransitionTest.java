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

import static com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunEvent.*;
import static com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-160")
@Tag("D-514")
@ExhaustiveTransitionExpectations(machine = MonthlyInvoiceRunStateMachine.class)
class MonthlyInvoiceRunTransitionTest {

    private record Row(MonthlyInvoiceRunStatus from, MonthlyInvoiceRunEvent event) {
    }

    private static final Map<Row, MonthlyInvoiceRunStatus> SPEC_TABLE = Map.of(
            new Row(null, RUN_STARTED), RUNNING,
            new Row(null, RUN_BLOCKED_BY_NIGHT), FAILED,
            new Row(RUNNING, RUN_FINISHED_CLEAN), COMPLETED,
            new Row(RUNNING, RUN_FINISHED_WITH_FAILURES), COMPLETED_WITH_FAILURES,
            new Row(RUNNING, RUN_FINISHED_WITH_DRAFTS_PENDING_REVIEW), AWAITING_REVIEW,
            new Row(AWAITING_REVIEW, RUN_REVIEW_COMPLETED), COMPLETED,
            new Row(RUNNING, RUN_ABORTED), FAILED);

    private static List<MonthlyInvoiceRunStatus> statesIncludingNone() {
        List<MonthlyInvoiceRunStatus> states = new ArrayList<>();
        states.add(null);
        states.addAll(EnumSet.allOf(MonthlyInvoiceRunStatus.class));
        return states;
    }

    static List<TransitionExpectation> expectedTransitions() {
        return statesIncludingNone().stream().flatMap(from -> EnumSet.allOf(MonthlyInvoiceRunEvent.class).stream()
                .map(event -> SPEC_TABLE.containsKey(new Row(from, event))
                        ? TransitionExpectation.allows(from, event, SPEC_TABLE.get(new Row(from, event)))
                        : TransitionExpectation.rejects(from, event, IllegalBillingRunTransitionException.class))).toList();
    }

    @Test
    void everyStatusAndEventPairMovesAsTheTableSaysOrIsRejected() {
        for (MonthlyInvoiceRunStatus from : statesIncludingNone()) {
            for (MonthlyInvoiceRunEvent event : MonthlyInvoiceRunEvent.values()) {
                MonthlyInvoiceRunStatus expected = SPEC_TABLE.get(new Row(from, event));
                if (expected == null) {
                    assertThatThrownBy(() -> MonthlyInvoiceRunStateMachine.resolveNextStatus(from, event)).isInstanceOf(IllegalBillingRunTransitionException.class);
                } else {
                    assertThat(MonthlyInvoiceRunStateMachine.resolveNextStatus(from, event)).isEqualTo(expected);
                }
            }
        }
    }
}
