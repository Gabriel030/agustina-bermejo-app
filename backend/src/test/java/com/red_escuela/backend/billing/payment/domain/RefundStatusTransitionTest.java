package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.billing.payment.exception.IllegalRefundStatusTransitionException;
import com.red_escuela.backend.shared.domain.statemachine.ExhaustiveTransitionExpectations;
import com.red_escuela.backend.shared.domain.statemachine.StateTransitionRow;
import com.red_escuela.backend.shared.domain.statemachine.TransitionExpectation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.red_escuela.backend.billing.payment.domain.RefundEvent.*;
import static com.red_escuela.backend.billing.payment.domain.RefundFixtures.ACTOR;
import static com.red_escuela.backend.billing.payment.domain.RefundStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-159")
@ExhaustiveTransitionExpectations(machine = RefundStatusStateMachine.class)
class RefundStatusTransitionTest {

    private record Row(RefundStatus from, RefundEvent event, RefundStatus to) {
    }

    private static final Set<Row> SPEC_TABLE = Set.of(
            new Row(null, REFUND_REQUESTED, REQUESTED),
            new Row(REQUESTED, REFUND_APPROVED, APPROVED),
            new Row(REQUESTED, REFUND_REJECTED, REJECTED),
            new Row(APPROVED, REFUND_COMPLETED, COMPLETED),
            new Row(APPROVED, REFUND_FAILED, FAILED));

    static List<TransitionExpectation> expectedTransitions() {
        List<RefundStatus> states = new ArrayList<>();
        states.add(null);
        states.addAll(EnumSet.allOf(RefundStatus.class));
        return states.stream().flatMap(from -> EnumSet.allOf(RefundEvent.class).stream()
                .map(event -> expectedTarget(from, event)
                        .map(to -> TransitionExpectation.allows(from, event, to))
                        .orElseGet(() -> TransitionExpectation.rejects(from, event, IllegalRefundStatusTransitionException.class)))).toList();
    }

    static Stream<Row> everyStatusAndEventPair() {
        return EnumSet.allOf(RefundStatus.class).stream().flatMap(from -> EnumSet.allOf(RefundEvent.class).stream()
                .map(event -> new Row(from, event, expectedTarget(from, event).orElse(null))));
    }

    private static Optional<RefundStatus> expectedTarget(RefundStatus from, RefundEvent event) {
        return SPEC_TABLE.stream().filter(row -> row.from() == from && row.event() == event).map(Row::to).findFirst();
    }

    private static void apply(Refund refund, RefundEvent event) {
        switch (event) {
            case REFUND_APPROVED -> refund.approve(RefundFixtures.REQUESTED_ON, ACTOR);
            case REFUND_REJECTED -> refund.reject("no", ACTOR);
            case REFUND_COMPLETED -> refund.complete(RefundFixtures.REQUESTED_ON, "REF-1", ACTOR);
            case REFUND_FAILED -> refund.fail("banco", ACTOR);
            case REFUND_REQUESTED -> RefundStatusStateMachine.resolveNextStatus(refund.getStatus(), event);
        }
    }

    @Test
    void theTransitionTableIsExactlyTheOneInTheSpecification() {
        Set<Row> actual = RefundStatusStateMachine.TRANSITIONS.listAllowedTransitions().stream()
                .map((StateTransitionRow<RefundStatus, RefundEvent> row) -> new Row(row.fromState(), row.event(), row.toState()))
                .collect(Collectors.toSet());

        assertThat(actual).isEqualTo(SPEC_TABLE);
    }

    @ParameterizedTest
    @MethodSource("everyStatusAndEventPair")
    void eachStatusAndEventPairEitherMovesAsTheTableSaysOrIsRejectedWithoutChangingAnything(Row pair) {
        Refund refund = RefundFixtures.in(pair.from());
        int changesBefore = refund.statusChanges().size();

        if (pair.to() == null) {
            assertThatThrownBy(() -> apply(refund, pair.event())).isInstanceOf(IllegalRefundStatusTransitionException.class);
            assertThat(refund.getStatus()).isEqualTo(pair.from());
            assertThat(refund.statusChanges()).hasSize(changesBefore);
            return;
        }
        apply(refund, pair.event());

        assertThat(refund.getStatus()).isEqualTo(pair.to());
        assertThat(refund.statusChanges()).hasSize(changesBefore + 1);
        RefundStatusChange written = refund.statusChanges().get(changesBefore);
        assertThat(written.getFromStatus()).isEqualTo(pair.from().name());
        assertThat(written.getToStatus()).isEqualTo(pair.to().name());
        assertThat(written.getEvent()).isEqualTo(pair.event().name());
        assertThat(written.getActorPersonId()).isEqualTo(ACTOR.personId());
    }

    @Test
    void terminalStatusesAcceptNoEvent() {
        for (RefundStatus terminal : List.of(REJECTED, COMPLETED, FAILED)) {
            for (RefundEvent event : RefundEvent.values()) {
                assertThatThrownBy(() -> RefundStatusStateMachine.resolveNextStatus(terminal, event)).isInstanceOf(IllegalRefundStatusTransitionException.class);
            }
        }
    }

    @Test
    void theOnlyCreationEventIsRequested() {
        for (RefundEvent event : RefundEvent.values()) {
            assertThat(RefundStatusStateMachine.TRANSITIONS.findTargetStateFor(null, event)).isEqualTo(expectedTarget(null, event));
        }
    }

    @Test
    void aNewRefundStartsRequestedWithItsInitialHistoryRow() {
        Refund refund = RefundFixtures.requested();

        assertThat(refund.getStatus()).isEqualTo(REQUESTED);
        assertThat(refund.statusChanges()).singleElement().satisfies(change -> {
            assertThat(change.getFromStatus()).isNull();
            assertThat(change.getToStatus()).isEqualTo("REQUESTED");
            assertThat(change.getEvent()).isEqualTo("REFUND_REQUESTED");
        });
    }
}
